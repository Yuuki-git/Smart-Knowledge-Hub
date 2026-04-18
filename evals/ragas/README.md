# Ragas Evaluation

This directory contains a baseline Ragas integration for evaluating the project RAG pipeline through existing APIs:

- `POST /api/search` for retrieved contexts
- `POST /api/chat` (SSE) for final answer

## 1. Install dependencies

```bash
cd evals/ragas
pip install -r requirements.txt
```

## 2. Prepare environment variables

At minimum:

```bash
# backend
set SKH_BASE_URL=http://127.0.0.1:8080

# judge LLM (Ragas evaluator)
set OPENAI_API_KEY=your_openai_api_key
set RAGAS_PROVIDER=openai
set RAGAS_JUDGE_MODEL=gpt-4o-mini
```

Optional (defaults shown):

```bash
set RAGAS_PROVIDER=openai
set RAGAS_JUDGE_MODEL=gpt-4o-mini
```

## 3. Dataset format

Input CSV columns:

- `question` (required)
- `expected_answer` (required)
- `top_k` (optional, default `5`)
- `model_provider` (optional, default `AUTO`)
- `scope_json` (optional JSON object)

Example file: `datasets/sample_eval.csv`

## 4. Run

From project root:

```bash
python evals/ragas/run_ragas_eval.py --dataset evals/ragas/datasets/sample_eval.csv
```

Useful options:

```bash
python evals/ragas/run_ragas_eval.py ^
  --dataset evals/ragas/datasets/sample_eval.csv ^
  --base-url http://127.0.0.1:8080 ^
  --judge-model gpt-4o-mini ^
  --output-dir evals/ragas/output ^
  --run-name ragas_eval_local
```

## 5. Output

After running, generated files are stored in `evals/ragas/output`:

- `<run_name>.csv`: flattened result rows
- `.ragas_backend/`: Ragas local backend artifacts

Output fields include:

- `question`
- `expected_answer`
- `response`
- `correctness` (`pass`/`fail`)
- `reason`
- `retrieved_context_count`
- `retrieved_contexts_json`
