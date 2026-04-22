from __future__ import annotations

import argparse
import asyncio
import csv
import json
import os
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any

import httpx
import pandas as pd
from dotenv import load_dotenv
from pydantic import BaseModel, Field
from ragas import experiment
from ragas.dataset import Dataset
from ragas.llms import llm_factory
from ragas.metrics import DiscreteMetric


class EvalInputRow(BaseModel):
    question: str = Field(min_length=1)
    expected_answer: str = Field(min_length=1)
    top_k: int = Field(default=5, ge=1, le=20)
    model_provider: str = Field(default="AUTO")
    scope: dict[str, Any] | None = None


class EvalOutputRow(BaseModel):
    question: str
    expected_answer: str
    response: str
    correctness: str
    reason: str | None = None
    retrieved_context_count: int = 0
    retrieved_contexts_json: str = "[]"


CORRECTNESS_PROMPT = """\
You are an evaluator for a RAG QA system.
Given question, expected answer, model response, and retrieved context:
- Return "pass" if the response is factually correct and consistent with expected answer.
- Return "fail" otherwise.
Also provide a short reason.

Question: {question}
Expected answer: {expected_answer}
Model response: {response}
Retrieved context:
{context}
"""


CORRECTNESS_METRIC = DiscreteMetric(
    name="correctness",
    prompt=CORRECTNESS_PROMPT,
    allowed_values=["pass", "fail"],
)


def _parse_scope(scope_value: str | None) -> dict[str, Any] | None:
    if scope_value is None:
        return None
    stripped = scope_value.strip()
    if not stripped:
        return None
    parsed = json.loads(stripped)
    if parsed is None:
        return None
    if not isinstance(parsed, dict):
        raise ValueError("scope_json must be a JSON object")
    return parsed


def load_eval_cases(csv_path: Path) -> list[EvalInputRow]:
    if not csv_path.exists():
        raise FileNotFoundError(f"Dataset file not found: {csv_path}")

    rows: list[EvalInputRow] = []
    with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for index, row in enumerate(reader, start=2):
            question = (row.get("question") or "").strip()
            expected_answer = (row.get("expected_answer") or "").strip()
            top_k_text = (row.get("top_k") or "").strip()
            model_provider = (row.get("model_provider") or "AUTO").strip() or "AUTO"
            scope_text = row.get("scope_json")
            try:
                top_k = int(top_k_text) if top_k_text else 5
            except ValueError as ex:
                raise ValueError(f"Invalid top_k at line {index}: {top_k_text}") from ex

            try:
                scope = _parse_scope(scope_text)
            except Exception as ex:
                raise ValueError(f"Invalid scope_json at line {index}: {scope_text}") from ex

            rows.append(
                EvalInputRow(
                    question=question,
                    expected_answer=expected_answer,
                    top_k=top_k,
                    model_provider=model_provider,
                    scope=scope,
                )
            )
    if not rows:
        raise ValueError("Dataset is empty.")
    return rows


def _extract_final_content_from_sse_payload(payload_text: str) -> str:
    payload_text = payload_text.strip()
    if not payload_text:
        return ""
    try:
        payload_json = json.loads(payload_text)
        if isinstance(payload_json, dict):
            return str(payload_json.get("content") or "")
        return str(payload_json)
    except json.JSONDecodeError:
        return payload_text


async def call_search(
    client: httpx.AsyncClient, base_url: str, row: EvalInputRow
) -> list[str]:
    payload: dict[str, Any] = {
        "query": row.question,
        "topK": row.top_k,
    }
    if row.scope:
        payload["scope"] = row.scope

    response = await client.post(f"{base_url}/api/search", json=payload)
    response.raise_for_status()
    body = response.json()
    results = body.get("results") or []
    contexts: list[str] = []
    for item in results:
        if isinstance(item, dict):
            text = item.get("text")
            if isinstance(text, str) and text.strip():
                contexts.append(text)
    return contexts


async def call_chat_sse(
    client: httpx.AsyncClient, base_url: str, row: EvalInputRow
) -> str:
    payload: dict[str, Any] = {
        "sessionId": f"ragas-{uuid.uuid4()}",
        "question": row.question,
        "modelProvider": row.model_provider,
        "topK": row.top_k,
    }
    if row.scope:
        payload["scope"] = row.scope

    event_name = ""
    data_lines: list[str] = []
    final_content = ""

    async with client.stream(
        "POST",
        f"{base_url}/api/chat",
        headers={"Accept": "text/event-stream"},
        json=payload,
    ) as response:
        response.raise_for_status()
        async for raw_line in response.aiter_lines():
            line = raw_line.rstrip("\r")
            if not line:
                if event_name == "final" and data_lines:
                    final_content = _extract_final_content_from_sse_payload(
                        "\n".join(data_lines)
                    )
                event_name = ""
                data_lines = []
                continue

            if line.startswith(":"):
                continue
            if line.startswith("event:"):
                event_name = line[len("event:") :].strip()
                continue
            if line.startswith("data:"):
                data_lines.append(line[len("data:") :].lstrip())

    return final_content


@experiment(EvalOutputRow)
async def evaluate_one(
    row: EvalInputRow, base_url: str, llm: Any, timeout_seconds: float
) -> EvalOutputRow:
    timeout = httpx.Timeout(timeout_seconds, connect=10.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        contexts = await call_search(client, base_url, row)
        answer = await call_chat_sse(client, base_url, row)

    metric_result = await CORRECTNESS_METRIC.ascore(
        llm=llm,
        question=row.question,
        expected_answer=row.expected_answer,
        response=answer,
        context="\n\n".join(contexts),
    )

    return EvalOutputRow(
        question=row.question,
        expected_answer=row.expected_answer,
        response=answer,
        correctness=str(metric_result.value),
        reason=metric_result.reason,
        retrieved_context_count=len(contexts),
        retrieved_contexts_json=json.dumps(contexts, ensure_ascii=False),
    )


def _to_dataset(
    rows: list[EvalInputRow], name: str, backend_root_dir: Path
) -> Dataset:
    backend_root_dir.mkdir(parents=True, exist_ok=True)
    df = pd.DataFrame([row.model_dump() for row in rows])
    return Dataset.from_pandas(
        dataframe=df,
        name=name,
        backend="local/csv",
        data_model=EvalInputRow,
        root_dir=str(backend_root_dir),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run RAG evaluation with Ragas.")
    parser.add_argument(
        "--dataset",
        default="evals/ragas/datasets/sample_eval.csv",
        help="Path to CSV dataset file.",
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("SKH_BASE_URL", "http://127.0.0.1:8080"),
        help="Base URL of Smart Knowledge Hub backend.",
    )
    parser.add_argument(
        "--provider",
        default=os.getenv("RAGAS_PROVIDER", "openai"),
        help="Ragas judge provider (e.g. openai).",
    )
    parser.add_argument(
        "--judge-model",
        default=os.getenv("RAGAS_JUDGE_MODEL", "gpt-4o-mini"),
        help="Judge model name used by Ragas.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=120.0,
        help="HTTP timeout for /api/chat and /api/search calls.",
    )
    parser.add_argument(
        "--output-dir",
        default="evals/ragas/output",
        help="Directory for result CSV and Ragas local backend data.",
    )
    parser.add_argument(
        "--run-name",
        default="",
        help="Optional run name. Default: ragas_eval_<timestamp>.",
    )
    return parser.parse_args()


async def main_async() -> int:
    load_dotenv()
    args = parse_args()

    dataset_path = Path(args.dataset).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    backend_root = output_dir / ".ragas_backend"

    eval_rows = load_eval_cases(dataset_path)

    if args.provider.lower() == "openai" and not os.getenv("OPENAI_API_KEY"):
        raise ValueError(
            "OPENAI_API_KEY is required when --provider=openai. "
            "Set it in environment variables before running."
        )

    run_name = args.run_name or f"ragas_eval_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    input_dataset_name = f"{run_name}_input"
    input_dataset = _to_dataset(eval_rows, input_dataset_name, backend_root)

    llm = llm_factory(model=args.judge_model, provider=args.provider)
    experiment_result = await evaluate_one.arun(
        input_dataset,
        name=run_name,
        base_url=args.base_url.rstrip("/"),
        llm=llm,
        timeout_seconds=args.timeout_seconds,
    )

    result_df = experiment_result.to_pandas()
    result_csv = output_dir / f"{run_name}.csv"
    result_df.to_csv(result_csv, index=False, encoding="utf-8-sig")
    experiment_result.save()

    pass_count = int((result_df["correctness"] == "pass").sum())
    total_count = int(len(result_df))
    pass_rate = (pass_count / total_count) if total_count else 0.0

    print(f"Run name: {run_name}")
    print(f"Result CSV: {result_csv}")
    print(f"Pass rate: {pass_count}/{total_count} = {pass_rate:.2%}")
    return 0


def main() -> int:
    try:
        return asyncio.run(main_async())
    except Exception as ex:
        print(f"Error: {ex}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
