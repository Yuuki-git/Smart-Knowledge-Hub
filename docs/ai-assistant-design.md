# Smart Knowledge Hub - RAG Assistant Design

## Goals
- Ingest PDF, Markdown, and Java source files.
- Build a semantic knowledge base with chunking + embeddings.
- Answer questions strictly from retrieved facts with citations.
- Support SSE streaming responses.
- Hybrid search (BM25 + vector) with top-k fusion.
- Session memory stored in Redis.
- Multi-LLM routing (DeepSeek, GPT-4o, local Llama).

## Non-Goals
- Full document management UI (out of scope for initial MVP).
- Real-time code execution or build pipelines.

## Architecture Overview
```
Client (Vue3) -> API Gateway (Spring Boot)
  ├─ Ingestion Service (Tika, chunker, embeddings)
  ├─ Retrieval Service (BM25 + Vector + Fusion)
  ├─ Chat Service (RAG + SSE streaming)
  ├─ Session Service (Redis)
  └─ Metadata DB (PostgreSQL)
Vector DB: Milvus / Chroma
Keyword Search: OpenSearch / Elasticsearch (BM25)
Object Storage: MinIO / S3 (raw files)
```

## Data Pipeline (Ingestion Flow)
1. Upload file -> store raw file in object storage.
2. Parse with Apache Tika:
   - PDF: extract text + page numbers.
   - Markdown: preserve headings + code blocks.
   - Java: use JavaParser / Tree-sitter to extract class/method structure.
3. Chunking (semantic):
   - Split on headings/sections; keep 200-400 tokens per chunk.
   - For Java: chunk by class, method, or logical blocks.
   - Overlap 10-20% tokens to reduce context loss.
4. Enrichment:
   - Add metadata: file name, page number, class name, method name.
   - Store structured references for citations.
5. Embedding:
   - Use `text-embedding-v3` (1536 dims).
6. Persist:
   - Chunk text + metadata in PostgreSQL.
   - Vector in Milvus/Chroma.
   - Keyword index in OpenSearch (BM25).

## Retrieval Flow
1. Query rewrite:
   - LLM rewrites vague questions into precise technical queries.
2. Hybrid search:
   - BM25 search in OpenSearch.
   - Vector search in Milvus/Chroma (cosine similarity).
3. Fusion:
   - Use Reciprocal Rank Fusion (RRF) or weighted sum.
4. Select top-k (default k=5):
   - Filter by similarity threshold to reduce hallucinations.
5. Assemble context:
   - Deduplicate by source file/class/page.

## Generation Flow
System prompt template:
```
You are a senior Java architect.
Only answer based on the Context.
If the Context does not contain the answer, say "I don't know based on the provided documents."
Always include citations with file/class/page references.
```

Output rules:
- Every response must list citations.
- If no sources, respond with "Not found in the uploaded documents."

## Session Memory
- Store conversation history in Redis by session ID.
- Maintain a sliding window for recent turns (e.g., last 10).
- Store only user/system/assistant messages, with timestamps.

## Multi-LLM Routing
Strategy pattern via Spring AI:
- Provider adapters:
  - DeepSeek API
  - OpenAI (GPT-4o)
  - Local Llama (HTTP endpoint)
- Runtime switching based on config or user choice.

## Data Model (PostgreSQL)
- `document`:
  - id, name, type, uploaded_by, storage_uri, created_at
- `chunk`:
  - id, document_id, text, token_count, page_no, class_name, method_name
- `embedding`:
  - chunk_id, vector_id, provider, created_at
- `ingestion_job`:
  - id, document_id, status, error, started_at, finished_at
- `conversation`:
  - id, session_id, created_at
- `message`:
  - id, conversation_id, role, content, created_at

## APIs (Spring Boot)
- `POST /api/files/upload` -> upload + create ingestion job.
- `GET /api/ingestion/{id}` -> job status.
- `POST /api/chat` -> SSE streaming RAG responses.
- `POST /api/search` -> hybrid search for debugging.
- `POST /api/sessions` -> create session.

## Error Handling and Safety
- Enforce file size and type restrictions.
- Reject unsupported formats.
- Use similarity thresholds to prevent weak context answers.
- Log every retrieval result for audit.

## Observability
- Trace IDs across ingestion, retrieval, and generation.
- Store retrieval evidence with response for audits.
- Metrics: ingestion throughput, retrieval latency, hallucination rate.

## Deployment Notes
- Split into services when scale demands:
  - Ingestion workers
  - Retrieval + Chat API
  - Vector DB / Search cluster
- Use Kubernetes or Docker Compose for MVP.
