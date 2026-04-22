# Smart Knowledge Hub（智能代码助手）

一个面向 **Spring Cloud 分布式架构文档、Java 代码与运维日志** 的 RAG 项目。  
目标是让回答“可检索、可溯源、可控幻觉”，并能在真实工程中落地。

## 项目亮点

- 多源入库：支持 `PDF`、`Markdown`、`Java` 文件上传与自动解析
- 混合检索：`OpenSearch(BM25)` + `Chroma(向量)` + `RRF` 融合排序
- 检索作用域：支持按 `documentId/fileName/className/methodName` 限定检索范围
- 多轮对话：基于 Redis 会话记忆，SSE 流式返回
- 回答溯源：`final` 结果携带 citations（文件/页码/类/方法）
- 多模型适配：`DEEPSEEK`、`OPENAI`、`OLLAMA`、`AUTO`
- 可选持久化：PostgreSQL 归档文档、切片、入库任务与会话消息

---

## 架构概览

1. **数据入库流**
   - `POST /api/files/upload`
   - `DocumentChunkingService` 解析 + 切片
   - 写入向量索引与关键词索引
   - 更新入库任务状态（`QUEUED/PROCESSING/INDEXED/EMPTY/FAILED`）

2. **检索增强流**
   - Query Rewrite（可选）
   - 向量检索 + BM25 关键词检索
   - RRF 融合排序输出 Top-K
   - Scope 过滤限定检索范围

3. **生成流**
   - Prompt 强约束：只允许依据检索上下文回答
   - SSE `delta` 流式输出
   - `final` 返回完整答案 + citations
   - grounded 校验失败时回退拒答

---

## 核心策略

### 1) 切片策略（Chunking）

实现：`src/main/java/com/smartknowledgehub/service/DocumentChunkingService.java`

- 通用切片
  - `MAX_CHARS = 1200`
  - `OVERLAP_PARAGRAPHS = 1`（相邻 chunk 保留段落重叠）
- PDF：PDFBox按页提取后再聚合，保留 `page_number`
- Markdown：先按标题（`#`）分 section，再段落聚合
- Java：优先按类/方法/构造函数切片，保留 `class_name`、`method_name`
- 兜底：JavaParser 或结构化解析失败时，自动降级为纯文本切片

统一元数据字段：

- `document_id`
- `chunk_index`
- `chunk_id`
- `file_name`
- `page_number`
- `class_name`
- `method_name`

### 2) 代码解析策略（Code Parsing）

- 解析器：`JavaParser`
- 路径：`CompilationUnit -> ClassOrInterfaceDeclaration -> Method/Constructor`
- 原则：**结构优先**，尽量让 1 个 chunk 对应 1 个语义单元
- 回退：解析异常不影响入库主流程

### 3) Query Rewrite

- 接口：`QueryRewriteService`
- 要求：保持原语言，只输出改写后的查询，不附加解释
- 回退：模型不可用/异常时回退原问题

### 4) 检索作用域（Scope Filter）

请求字段：

- `documentId`
- `fileName`
- `className`
- `methodName`

生效接口：

- `POST /api/search`
- `POST /api/chat`

说明：

- 作用域为精确匹配
- 空字段不参与过滤
- 作用域过窄会导致检索为空

### 5) 反幻觉策略（Hallucination Guardrails）

- 检索为空：直接返回 `Not found in the uploaded documents.`
- Prompt 约束：仅能依据 `[Context]` 回答
- 最终闸门：`AnswerGroundingValidator`
  - `MIN_OVERLAP_TOKENS = 2`
  - `MIN_SENTENCE_LENGTH = 6`
- 校验失败：强制回退拒答文本

---

## 技术栈

- 后端：`Spring Boot 3.5.x`、`Spring WebFlux`、`Spring AI`
- 检索：`OpenSearch`、`Chroma`
- 解析：`Apache Tika`、`PDFBox`、`JavaParser`
- 缓存/会话：`Redis`
- 持久化（可选）：`PostgreSQL`
- 前端：`Vue 3` + `Tailwind CSS` + `Vite`
- AI: Codex 5.3
---

## 快速开始

### 环境要求

- JDK `17+`
- Maven `3.9+`
- Node.js `20+`（前端开发/打包）
- Redis（会话必需）
- 可选：OpenSearch、Chroma、PostgreSQL

### 1. 启动后端（默认无 Chroma）

```powershell
mvn spring-boot:run
```

默认行为：

- 排除 Chroma 自动装配
- `app.vector.enabled=false`

### 2. 启动后端（启用 Chroma）

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=chroma
```

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

访问：`http://127.0.0.1:5173`

### 4. 编译与打包

仅后端编译检查：

```powershell
mvn -DskipTests compiler:compile
```

完整打包（含前端构建）：

```powershell
mvn -DskipTests package
```

[//]: # (---)

[//]: # ()
[//]: # (## 关键配置)

[//]: # ()
[//]: # (主配置文件：`src/main/resources/application.yaml`  )

[//]: # (Chroma Profile：`src/main/resources/application-chroma.yaml`)

[//]: # ()
[//]: # (关键开关：)

[//]: # ()
[//]: # (- `app.vector.enabled`)

[//]: # (- `app.search.enabled`)

[//]: # (- `app.rewrite.enabled`)

[//]: # (- `app.persistence.postgres.enabled`)

[//]: # ()
[//]: # (常用环境变量：)

[//]: # ()
[//]: # (- `DEEPSEEK_API_KEY`)

[//]: # (- `OPENAI_API_KEY`)

[//]: # (- `OLLAMA_BASE_URL`)

[//]: # (- `REDIS_HOST`、`REDIS_PORT`)

[//]: # (- `OPENSEARCH_ENABLED`、`OPENSEARCH_BASE_URL`)

[//]: # (- `CHROMA_HOST`、`CHROMA_PORT`)

[//]: # (- `POSTGRES_ENABLED`、`POSTGRES_URL`、`POSTGRES_USERNAME`、`POSTGRES_PASSWORD`)

[//]: # ()
[//]: # (注意：若 `app.search.enabled=false` 且 `app.vector.enabled=false`，系统无法召回上下文，问答会稳定返回拒答文本。)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## API 说明)

[//]: # ()
[//]: # (### 1&#41; 上传文件)

[//]: # ()
[//]: # (`POST /api/files/upload`（`multipart/form-data`）)

[//]: # ()
[//]: # (返回示例：)

[//]: # ()
[//]: # (```json)

[//]: # ({)

[//]: # (  "documentId": "3e497f40-7f12-4e70-97f8-4f9dcd1298b8",)

[//]: # (  "jobId": "ad0b6953-8c9b-4b04-b16f-bfba04a9de5c",)

[//]: # (  "status": "QUEUED")

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (### 2&#41; 入库状态)

[//]: # ()
[//]: # (`GET /api/ingestion/{jobId}`)

[//]: # ()
[//]: # (### 3&#41; 检索调试)

[//]: # ()
[//]: # (`POST /api/search`)

[//]: # ()
[//]: # (```json)

[//]: # ({)

[//]: # (  "query": "Nacos 配置中心集群部署",)

[//]: # (  "topK": 5,)

[//]: # (  "scope": {)

[//]: # (    "fileName": "nacos-config.md",)

[//]: # (    "className": "NacosConfigService")

[//]: # (  })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (### 4&#41; 对话（SSE）)

[//]: # ()
[//]: # (`POST /api/chat`)

[//]: # ()
[//]: # (```json)

[//]: # ({)

[//]: # (  "sessionId": "demo-session",)

[//]: # (  "question": "Nacos 集群如何配置？",)

[//]: # (  "modelProvider": "AUTO",)

[//]: # (  "topK": 5,)

[//]: # (  "scope": {)

[//]: # (    "documentId": "3e497f40-7f12-4e70-97f8-4f9dcd1298b8")

[//]: # (  })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (cURL 示例：)

[//]: # ()
[//]: # (```bash)

[//]: # (curl -N -X POST "http://127.0.0.1:8080/api/chat" \)

[//]: # (  -H "Content-Type: application/json" \)

[//]: # (  -H "Accept: text/event-stream" \)

[//]: # (  -d '{"sessionId":"demo","question":"Nacos 集群如何配置？","modelProvider":"AUTO","topK":5,"scope":{"fileName":"nacos-config.md"}}')

[//]: # (```)

[//]: # ()
[//]: # (SSE 事件：)

[//]: # ()
[//]: # (- `event: delta`：增量 token)

[//]: # (- `event: final`：完整答案 + citations)

[//]: # ()
[//]: # (`final` 示例：)

[//]: # ()
[//]: # (```json)

[//]: # ({)

[//]: # (  "type": "final",)

[//]: # (  "content": "...",)

[//]: # (  "citations": [)

[//]: # (    {)

[//]: # (      "sourceType": "chunk",)

[//]: # (      "sourceRef": "file=nacos-config.md | class=NacosConfigService | method=loadConfig",)

[//]: # (      "snippet": null)

[//]: # (    })

[//]: # (  ],)

[//]: # (  "done": true)

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## PostgreSQL 持久化（可选）)

[//]: # ()
[//]: # (开启：)

[//]: # ()
[//]: # (- `app.persistence.postgres.enabled=true`)

[//]: # ()
[//]: # (自动建表（默认 `app.persistence.postgres.init-schema=true`）：)

[//]: # ()
[//]: # (- `skh_document`)

[//]: # (- `skh_chunk`)

[//]: # (- `skh_ingestion_job`)

[//]: # (- `skh_conversation`)

[//]: # (- `skh_message`)

[//]: # ()
[//]: # (用途：)

[//]: # ()
[//]: # (- 文档与切片元数据归档)

[//]: # (- 入库任务状态追踪)

[//]: # (- 会话消息长期存储)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## 常见问题)

[//]: # ()
[//]: # (### 1&#41; `Error creating bean 'vectorStore'`)

[//]: # ()
[//]: # (通常是 Chroma 不可达：)

[//]: # ()
[//]: # (- 不需要向量检索：使用默认启动方式（不带 `chroma` profile）)

[//]: # (- 需要向量检索：检查 Chroma 地址、端口、鉴权与网络连通性)

[//]: # ()
[//]: # (### 2&#41; 前端 `localhost:5173` 无法访问)

[//]: # ()
[//]: # (确认前端开发服务已启动：)

[//]: # ()
[//]: # (```powershell)

[//]: # (cd frontend)

[//]: # (npm run dev -- --host 127.0.0.1 --port 5173)

[//]: # (```)

[//]: # ()
[//]: # (### 3&#41; 对话返回“请求失败，请检查后端服务或网络”)

[//]: # ()
[//]: # (建议按顺序排查：)

[//]: # ()
[//]: # (1. 后端是否在 `127.0.0.1:8080` 正常运行)

[//]: # (2. 浏览器 Network 中 `/api/chat` 的状态码)

[//]: # (3. Redis 是否可用)

[//]: # (4. 是否至少启用了一个检索后端并完成入库)

[//]: # ()
[//]: # (---)

## 目录结构

- `src/main/java/com/smartknowledgehub/api`：REST 接口层
- `src/main/java/com/smartknowledgehub/service`：入库/检索/对话核心
- `src/main/java/com/smartknowledgehub/model`：请求/响应/领域模型
- `src/main/java/com/smartknowledgehub/config`：配置与运行时开关
- `src/main/resources`：应用配置与静态资源
- `frontend`：Vue 前端工程
- `docs/ai-assistant-design.md`：设计草案

---

## 当前状态

已完成：

- 多格式文档解析与切片
- 混合检索与 RRF 融合
- 检索作用域全链路生效
- SSE 流式问答 + 引用溯源
- Query Rewrite、Redis 会话、PostgreSQL 可选持久化

