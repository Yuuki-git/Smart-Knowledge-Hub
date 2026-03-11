# Smart Knowledge Hub

`Smart Knowledge Hub` 是一个基于 `Spring Boot + Spring AI` 的 RAG 智能助手，面向 `Spring Cloud` 架构文档、Java 代码与运维知识分析场景。

## 主要能力

- 支持上传 `PDF`、`Markdown`、`Java` 文件。
- 自动解析并切片（页级/标题级/类方法级）。
- 混合检索：`OpenSearch(BM25)` + `Milvus(向量)`，并用 `RRF` 融合排序。
- 对话接口 `POST /api/chat` 采用 `SSE` 流式返回。
- 回答附带引用信息（文件/页码/类名/方法名等）。
- `Redis` 存储会话消息。
- 多模型路由：`DEEPSEEK`、`OPENAI`、`OLLAMA`、`AUTO`。

## 当前进度

已可用：
- 上传与入库流程。
- OpenSearch 关键词索引与检索。
- Milvus 向量索引与检索（可选开关）。
- Vue 前端聊天页（流式输出 + 引用展示）。

待完善：
- PostgreSQL 全量持久化（`document/chunk/conversation/job`）。
- 生产化能力（鉴权、多租户隔离、监控、测试）。

## 技术栈

- 后端：`Spring Boot 3.5.x`、`Spring WebFlux`、`Spring AI`。
- 检索：`OpenSearch`（BM25）、`Milvus`（向量，可选）。
- 会话：`Redis`。
- 解析：`Apache Tika`、`PDFBox`、`JavaParser`。
- 前端：`Vue 3` + `Tailwind` + `Vite`。

## 切片策略（Chunking Strategy）

当前切片实现位于 `DocumentChunkingService`，核心目标是“尽量保留语义边界 + 可追溯元数据”。

- 通用聚合策略：
  - 按段落聚合为 chunk，默认 `MAX_CHARS = 1200`。
  - 邻接 chunk 保留重叠段落，默认 `OVERLAP_PARAGRAPHS = 1`，降低上下文断裂。
- PDF 策略：
  - 先按页提取文本，再做段落聚合。
  - 元数据保留 `page_number`，用于引用定位。
- Markdown 策略：
  - 按标题行（`#`）切分为 section，再做段落聚合。
  - 保留文件级来源信息。
- Java 策略：
  - 以类/方法/构造函数为主切片单元。
  - 方法和构造函数优先作为独立 chunk。
  - 仅有类声明（无方法/构造）时，按类体文本切片。
- 兜底策略：
  - 非上述类型走 Tika 解析并按纯文本段落切片。
- 统一元数据：
  - `document_id`、`chunk_index`、`file_name`、`page_number`、`class_name`、`method_name`、`chunk_id`。

## 代码解析策略（Code Parsing Strategy）

当前代码解析目标是“结构优先，而非按字数硬切”。

- 解析器：`JavaParser`。
- 解析过程：
  - 读取 `CompilationUnit`。
  - 遍历 `ClassOrInterfaceDeclaration`。
  - 提取每个类下的方法与构造函数。
  - 以 `method.toString()` / `constructor.toString()` 作为 chunk 内容。
- 解析异常处理：
  - Java 语法不完整或解析失败时，自动降级为纯文本切片，不中断入库流程。
- 设计原则：
  - 优先保持“一个 chunk 对应一个代码语义单元（方法/构造）”。
  - 保留 `class_name` 与 `method_name` 元数据用于引用与检索过滤。

## 检索与融合策略

- Query Rewrite：
  - 可选开关 `app.rewrite.enabled`。
  - 开启后先用 LLM 将用户问题改写为更可检索的查询。
- 关键词检索：
  - OpenSearch `match(text)`，走 BM25 打分。
- 向量检索：
  - Milvus 相似度检索（可选开关）。
- 混合融合：
  - 使用 RRF（Reciprocal Rank Fusion），公式近似 `1 / (rrfK + rank)`。
  - 默认参数在 `app.retrieval.rrf-k`。

## 反幻觉策略（Hallucination Guardrails）

当前是“检索约束 + 生成约束 + 最终闸门”三层机制：

- 检索闸门：
  - 若检索上下文为空，直接返回 `Not found in the uploaded documents.`。
- Prompt 约束：
  - 系统提示要求仅依据 `Context` 作答，未命中必须拒答。
- 最终答案闸门：
  - 在 `final` 输出前做 grounded 校验（`AnswerGroundingValidator`）。
  - 策略为“按句分割答案，与检索上下文 token 重叠匹配”。
  - 默认阈值：
    - `MIN_OVERLAP_TOKENS = 2`
    - `MIN_SENTENCE_LENGTH = 6`
  - 若校验失败，强制回退拒答文本，不返回编造内容。
- 来源溯源：
  - `final` 事件携带 `citations`，包含文件/页码/类名/方法名来源。

## 本地运行

### 1. 启动依赖服务

- `Redis`（会话记忆必需）。
- `OpenSearch`（关键词检索可选）。
- `Milvus`（向量检索可选，启用向量模式时需要）。

### 2. 启动后端（默认无 Milvus 模式）

```powershell
mvn spring-boot:run
```

默认行为：
- 排除 Milvus 自动装配。
- 关闭向量检索。
- 仅关键词检索也可运行。

### 3. 启动后端（启用 Milvus）

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=milvus
```

启用前请确认 Milvus 可访问。

### 4. 启动前端开发服务

```powershell
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

访问地址：
- `http://127.0.0.1:5173`

前端默认请求后端：
- `http://127.0.0.1:8080`

可通过环境变量覆盖：
- `VITE_API_BASE`

## 打包（后端 + 前端静态资源）

```powershell
mvn -DskipTests package
```

`pom.xml` 已配置前端构建并复制到 Spring 静态资源目录。

## 配置说明

主要配置：
- `src/main/resources/application.yaml`

Milvus 配置文件：
- `src/main/resources/application-milvus.yaml`

关键开关：
- `app.vector.enabled`
- `app.search.enabled`
- `app.rewrite.enabled`

常用环境变量：
- `DEEPSEEK_API_KEY`
- `OPENAI_API_KEY`
- `REDIS_HOST`、`REDIS_PORT`
- `OPENSEARCH_BASE_URL`、`OPENSEARCH_ENABLED`
- `MILVUS_HOST`、`MILVUS_PORT`

## 接口列表

- `GET /api/chat`：接口用法说明。
- `POST /api/chat`：SSE 流式对话。
- `POST /api/files/upload`：文件上传（multipart）。
- `GET /api/ingestion/{jobId}`：入库任务状态。
- `POST /api/search`：混合检索调试接口。
- `POST /api/index`：手动索引调试接口。

### `POST /api/chat` 示例

```bash
curl -N -X POST "http://127.0.0.1:8080/api/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"sessionId":"demo","question":"Nacos 集群如何配置？","modelProvider":"AUTO","topK":5}'
```

## 常见问题

- 访问 `localhost:5173` 拒绝连接：
  - 前端开发服务未启动，请运行 `npm run dev ...` 并确认 5173 端口监听。
- `Error creating bean 'vectorStore'`：
  - 默认使用无 Milvus 模式；若启用 `milvus` profile，需确保 Milvus 可达。
- 前端提示 `请求失败，请检查后端服务或网络`：
  - 先确认后端 `127.0.0.1:8080` 正常，再检查浏览器 Network 中 `/api/chat` 的状态码。

## 目录结构

- `src/main/java/com/smartknowledgehub/api`：REST 接口层。
- `src/main/java/com/smartknowledgehub/service`：入库、检索、对话核心服务。
- `src/main/java/com/smartknowledgehub/config`：配置与运行时开关。
- `src/main/resources`：应用配置与静态资源。
- `frontend`：Vue 前端工程。
- `docs/ai-assistant-design.md`：系统设计草案。
