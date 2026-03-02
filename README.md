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
