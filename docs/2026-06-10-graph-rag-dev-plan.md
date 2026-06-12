# Agentic Graph RAG 知识库 — 开发计划书

> 配套设计：`docs/2026-06-10-agentic-graph-rag-kb.md`
> 定位：在现有里程碑之后新增「Graph RAG 知识库」专项，分 P0~P9 阶段推进。
> 已确认决策：统一 LightRAG / PG+AGE / 共享 KB+文档过滤 / 完整 agent loop / DeepSeek+bge-m3 / opendataloader（容器内 JRE）。单文档过滤限制与新增依赖：用户已批准。P8 已完成旧管线下线。

---

## 0. 总体原则

- **设计先行已完成**：本计划仅排执行顺序，编码严格依据设计文档，遵循最小改动。
- **分支**：每阶段从 `main` 切 `<type>(<scope>)/<desc>`，scope 用 `ai`（Python）/ `db`（迁移）/ `web`（前端）/ `common`。
- **提交粒度**：每阶段内按模块小步提交，不混提；不自动 push，推送前确认。
- **验收**：每阶段给出可运行的验收命令/标准，达标方可进入下一阶段。
- **依赖批准**：`lightrag-hku`、`opendataloader-pdf`、bge-m3 运行时、JRE、PG+AGE 镜像替换 —— 已获批准。

### 阶段总览

| 阶段 | 名称 | 关键交付 | 状态 |
|------|------|---------|------|
| P0 | 基础设施与依赖准备 | PG+AGE 镜像、AI 容器 JRE+依赖、bge-m3 | 完成 |
| P1 | LightRAG 接入骨架 | workspace 级 LightRAG PG 后端 | 完成 |
| P2 | 文档解析管线 | PDF→Markdown + MD 直读 | 完成 |
| P3 | KB 数据模型与 Java 接口 | MySQL V4 + `/api/kb/**` + 归属校验 | 完成 |
| P4 | 增量索引任务链路 | Celery 索引任务 + 串行锁 + 状态机 + 删除 | 完成 |
| P5 | Agent Loop（整库 QA） | 分解/检索/评估/再检索/引用/SSE | 完成 |
| P6 | 单文档过滤 QA | 按 doc_id 过滤 + 放大召回 | 完成 |
| P7 | 前端切片 | KB 管理页 + 加文档 + 问答页 | 完成 |
| P8 | 旧管线下线 | 移除自研 chunk RAG 与旧接口 | 完成 |
| P9 | 测试 / 验收 / PR | 自动化、CI、E2E 脚本、文档同步 | 本地验收完成 |

---

## P0：基础设施与依赖准备

**目标**：所有外部依赖就绪并验证连通，后续阶段不再被环境阻塞。

后端/基础设施任务：
- `docker-compose.yml`：PostgreSQL 由 `pgvector/pgvector:pg16` 换为 **pgvector+AGE 自定义镜像**（参考 `ref/LightRAG/Dockerfile.postgres`，pg18 + AGE 1.7.0），加 `shared_preload_libraries=age`，init 脚本建 `vector` + `age` 扩展。
- AI 服务 `Dockerfile`：安装 **JRE**；GPU 直通配置（compose `deploy.resources` / `--gpus all`）。
- `ai-service/requirements.txt` 新增：`lightrag-hku`、`opendataloader-pdf`、`FlagEmbedding`（或 `sentence-transformers`，加载 bge-m3）。
- `config.py` / `.env.example` 新增：`embed_provider`、`embed_model=bge-m3`、`postgres_age_dsn`、`lightrag_workspace_prefix`、`agent_max_iters`、`agent_timeout_s`。

验收：
- `docker compose up -d` 后 PG 内 `SELECT * FROM ag_catalog.ag_graph;`（AGE 可用）、`CREATE EXTENSION vector` 已建。
- AI 容器内 `java -version` 正常；`python -c "import lightrag, opendataloader_pdf"` 成功。
- bge-m3 加载并产出向量（GPU 命中或 CPU 降级日志可见）。

分支：`chore(ai)/graph-rag-infra`

---

## P1：LightRAG 接入骨架

**目标**：在 AI 服务内以 PG+AGE 为后端，跑通最小「插入→查询」。

任务：
- 新建 `app/services/lightrag_service.py`：封装 `LightRAG` 实例化（KV/向量/图/doc-status 均 PG 后端，workspace=`kb_{id}`），注入 `llm_func`（DeepSeek）+ `embedding_func`（bge-m3）。
- 实例缓存：按 workspace 复用 LightRAG 实例（避免重复初始化）。
- 冒烟脚本：`ainsert` 一段文本 → `aquery(mode="mix")` 返回结果。

验收：脚本能对一个临时 workspace 完成插入与查询，PG 中可见图节点/向量行。

分支：`feat(ai)/lightrag-skeleton`

---

## P2：文档解析管线

**目标**：把 PDF / MD 统一转成可入库文本。

任务：
- 新建 `app/services/parse_service.py`：
  - PDF（论文/书籍）→ `opendataloader_pdf.convert(format=["markdown"])` → Markdown 文本。
  - Markdown 笔记 → 直接读取。
  - 异常处理：解析失败返回明确错误，供任务标 `failed`。
- 下载复用现有「Java 内部下载 URL（短期签名）」机制。

验收：对样例论文 PDF、书籍 PDF、含表格 PDF、MD 笔记各跑一遍，产出 Markdown 质量人工抽检通过。

分支：`feat(ai)/pdf-parse`

---

## P3：KB 数据模型与 Java 接口

**目标**：知识库与文档的业务实体、权限、状态在 Java 侧落地。

任务：
- Flyway `V4__kb_graph_rag.sql`：`kb_knowledge_base`、`kb_document`（字段见设计 §5.1，含 userId 归属、唯一去重约束）。
- 实体 / Mapper / Repository（MyBatis Plus）。
- `application/ai/` 扩展 `AiServiceClient`：新增 KB 索引、删除、问答代理方法；`AiKbIndexRequest` 等 DTO。
- `api/controller/KbController.java`：`/api/kb/**`（见设计 §5.2），全部校验 `userId` 归属；问答接口 SSE 透传 AI 服务。
- 错误用 `BusinessException(ErrorCode.XXX)`；外部 HTTP 调用带超时。

验收：`mvn compile -q`；`mvn test`（KB 接口归属校验、CRUD 单测）；Flyway 迁移成功。

分支：`feat(ai)/kb-java-api`（DB 迁移可拆 `feat(db)/kb-schema` 子提交）

---

## P4：增量索引任务链路

**目标**：加文档 → 异步解析+索引 → 状态可查；支持删除。

任务：
- `app/api/kb_router.py`：`POST /kb/{kbId}/index`、`DELETE /kb/{kbId}`、`DELETE /kb/{kbId}/doc/{docId}`、`GET /kb/task/{taskId}`。
- `app/tasks/kb_index_tasks.py`（Celery）：下载→解析（P2）→`ainsert`（P1），写回 `lightrag_doc_id`。
- **串行锁**：Redis 分布式锁 `lightrag:lock:kb_{id}`，同 KB 串行、跨 KB 并行。
- **状态机**：`pending→parsing→indexing→indexed/failed`，回写 MySQL（经 Java 或共享状态）。
- 删除：`adelete_by_doc_id` / 删 workspace。

验收：加文档后状态流转正确；重复文档去重；删除后再查询不含其证据；同 KB 并发提交被串行化。

分支：`feat(ai)/kb-index-task`

---

## P5：Agent Loop（整库问答）

**目标**：完整 agent loop 驱动整库 QA，SSE 流式 + 引用。

任务：
- `app/services/agent_service.py`：
  - 路由（整库/单文档）、查询分解（DeepSeek）、检索（`aquery_data(mode="mix")` 取原始结果）、充分性评估、再检索、引用聚合、生成。
  - `agent_max_iters`（默认 3）、`agent_timeout_s`（默认 60）生效。
- `POST /kb/{kbId}/chat` SSE 输出；Java `/api/kb/{kbId}/chat` 透传。

验收：复杂多跳问题能触发分解+多轮检索；返回带来源引用；迭代上限/超时可观测生效。

分支：`feat(ai)/agent-loop`

---

## P6：单文档过滤 QA

**目标**：在共享 KB 上实现单文档 QA。

任务：
- agent loop 增加 `docId` 模式：放大 `top_k`/`chunk_top_k`，召回后按 `file_path`/`full_doc_id` 过滤。
- `POST /kb/{kbId}/documents/{docId}/chat`（Java）→ AI `/kb/{kbId}/chat` 带 `docId`。

验收：单文档问答的证据均来自目标文档；与整库 QA 对比无串档。

分支：`feat(ai)/single-doc-qa`

---

## P7：前端切片

**目标**：用户可建库、加文档、看索引状态、问答。

任务（`web/`）：
- 知识库列表 / 创建 / 删除页。
- 从文件管理「加入知识库」入口；文档列表与索引状态轮询。
- 问答页：整库 / 单文档两种入口，SSE 流式渲染 + 引用展示。
- API client、zustand/react-query 接入，组件 PascalCase / hooks `useXxx`。

验收：`npm --prefix web run build`、`npm --prefix web run lint`；端到端手动走通建库→加文档→问答。

分支：`feat(web)/kb-ui`

---

## P8：旧管线下线

**目标**：完成决策1的统一，移除自研 chunk RAG。

任务：
- 确认前端不再依赖 `/v1/search/*`、`/v1/chat`、旧 `/internal/index`。
- 移除 `embedding_service.py` / `search_service.py` / `chat_service.py` / `vector_doc` 及相关路由、Celery 旧任务。
- 清理 `requirements.txt` 中仅旧管线用的依赖（如 `pypdf`，若 opendataloader 已覆盖）。

验收：旧接口下线后回归通过；AI 服务启动无悬挂引用。

分支：`refactor(ai)/retire-legacy-rag`

---

## P9：测试 / 验收 / PR

任务：
- 单测：Java（KB 接口+归属）、AI（pytest：解析、索引、过滤、agent loop 上限）。
- 集成：建库→加文档→索引→整库 QA→单文档 QA→删除 全链路。
- 人工验收：提供步骤交用户验证，处理反馈。
- 文档同步：回填设计文档 §9 测试结果；更新 `development-plan.md` 里程碑表与 `overview-design.md` § 5.2。
- CI 全绿后按模板写 PR。

验收：`mvn test` + AI pytest + 前端 build/lint 全过；CI 绿；用户验收通过。

分支：随各阶段；最终 PR 汇总。

当前结果（2026-06-11）：

- Java 98 项、AI 24 项测试通过；Web lint/build 通过。
- GitHub Actions 已增加 Java、AI、Web 三个独立质量门禁。
- AI 服务默认只在 Compose 内网暴露；开发时通过 `docker-compose.dev.yml` 显式发布端口。
- `scripts/p9_kb_e2e.py` 已覆盖建库、加文档、索引、两类问答和清理。
- 真实 DeepSeek + bge-m3 + PG/AGE + Redis + Celery E2E 已通过。
- 远端 CI 待分支 push 后验证。

---

## 风险与回滚

- **索引慢/贵**（实体抽取每 chunk 调 LLM）：P4 设批量与并发上限；大书籍先小样本验证。
- **AGE/PG 迁移**：P0 在独立环境验证镜像，保留旧 `postgres_data` 卷备份。
- **旧管线回滚**：P8 已完成旧接口和实现下线；如需回滚，按 P8 独立提交恢复，
  不与新 KB 数据混写。
- **GPU 不可用**：bge-m3 CPU 降级路径在 P0 验证。
