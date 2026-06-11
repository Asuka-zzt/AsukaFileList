# AsukaFileList

AsukaFileList 目标是逐步搭建一个 Java 版本的 AList，并通过 Python RAG 服务提供语义搜索与知识库问答能力。

## 项目结构

```text
.
├── ai-service/                 # Python FastAPI RAG 服务
├── docs/                       # 概要设计与详细设计
├── ref/                        # 参考项目源码
├── src/main/java/              # Java 主服务
├── src/main/resources/         # Java 主服务配置与 Flyway 迁移脚本
├── src/test/java/              # Java 测试
├── web/                        # 前端 (Vite + React + TS)
├── docker-compose.yml          # 本地基础设施（MySQL/PostgreSQL/Redis）
└── pom.xml                     # Maven 工程
```

## 本地快速启动

### 前置条件

- Java 17+
- Maven 3.8+
- Node.js 18+ (for frontend)
- Docker & Docker Compose
- tmux (可选，用于持久化启动后端/前端，防止在 AI 环境中因超时被终止)

### 第一步：准备环境变量（重要）

```bash
cp .env.example .env
# 编辑 .env
# - 至少设置 DB_PASSWORD、PG_PASSWORD 等
# - **必须设置 ASUKA_ADMIN_PASSWORD**（后端启动时会校验，否则会报错 "ASUKA_ADMIN_PASSWORD must be configured"）
#   开发时可设为 admin123
```

示例关键项：

```env
ASUKA_ADMIN_USERNAME=admin
ASUKA_ADMIN_PASSWORD=admin123   # 开发测试用，生产请用强密码
ASUKA_LOCAL_ROOT_WHITELIST=/tmp/asuka-file-list
```

### 第二步：启动基础设施

```bash
# 启动 MySQL、PostgreSQL、Redis
docker compose up -d mysql postgres redis

# 等待健康检查通过（约 20 秒）
docker compose ps
```

### 第三步：启动 Java 主服务（后端）

**方式一：直接本地运行（需先加载 .env）**

```bash
# 加载 .env 环境变量（健壮方式，自动忽略注释和空行）
export $(grep -v '^#' .env | grep -v '^$' | sed 's/ *#.*//' | xargs)

mvn spring-boot:run
```

**方式二：使用 tmux 持久化运行（推荐在当前 AI/工具环境中，防止超时被杀）**

```bash
tmux new-session -d -s asuka-backend -c . '
set -a
while IFS="=" read -r k v || [ -n "$k" ]; do
  k=$(echo "$k" | sed "s/^[[:space:]]*//;s/[[:space:]]*$//")
  [[ $k =~ ^# ]] || [[ -z $k ]] && continue
  v=$(echo "$v" | sed "s/[[:space:]]*#.*//; s/[[:space:]]*$//")
  export "$k=$v"
done < .env
set +a
echo "=== Env loaded. Starting backend ==="
mvn spring-boot:run -B -DskipTests -Dmaven.javadoc.skip=true --no-transfer-progress
'

# 查看日志
tmux capture-pane -t asuka-backend -p | tail -100
# 实时附加：tmux attach -t asuka-backend （Ctrl-b d 退出）
```

服务启动后 Flyway 会自动建表。访问验证：

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health

# 测试 admin 登录（密码来自 .env 的 ASUKA_ADMIN_PASSWORD）
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 第四步（可选）：启动 Python AI 服务

```bash
cd ai-service
pip install -r requirements.txt
uvicorn app.main:app --reload

# 异步任务 worker
celery -A app.core.celery_app worker --loglevel=info
```

AI API 是 Java 主服务使用的内部接口。Compose 默认不向宿主机发布 8000 端口；
本地直接运行 `uvicorn` 时可将 Java 的 `AI_SERVICE_BASE_URL` 设为
`http://localhost:8000`。

### 第五步（可选）：启动 Web 前端（AList 风格）

**方式一：直接本地运行**

```bash
cd web
npm install
npm run dev
```

**方式二：使用 tmux 持久化运行（推荐）**

```bash
tmux new-session -d -s asuka-frontend -c web 'npm run dev'

# 查看日志
tmux capture-pane -t asuka-frontend -p | tail -100
# 实时附加：tmux attach -t asuka-frontend （Ctrl-b d 退出）
```

前端默认运行在 **http://localhost:5174** ，开发时代理 `/api` 到后端 8080。

使用 admin 账号登录（密码 = 你在 .env 设置的 `ASUKA_ADMIN_PASSWORD`）后即可：

- 浏览虚拟挂载的存储（/api/fs/list）
- 管理存储（需要 admin 权限）

详细设计见 `docs/web-frontend-design.md`。

### 使用 Docker 完整启动（生产模拟）

```bash
# 构建并启动 infra + java-service + ai-service
docker compose --profile app up -d --build

# 查看日志
docker compose logs -f java-service
docker compose logs -f ai-service
```

访问同上：后端 8080，前端需单独构建或用 volume 挂载（开发仍推荐本地 npm run dev）。

需要从宿主机直接调试容器内 AI API 时，显式叠加开发配置：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml \
  --profile app up -d --build
```

### 停止服务（tmux 方式）

```bash
tmux kill-session -t asuka-frontend
tmux kill-session -t asuka-backend
# 或全部
tmux kill-server
```

## 运行测试

```bash
# 单元测试（不需要 MySQL，使用 H2 内存库）
mvn test

# 编译检查
mvn compile -q

# AI 单元测试
python -m pip install -r ai-service/requirements-dev.txt
python -m pytest ai-service/tests -q

# 前端质量门禁
npm --prefix web ci
npm --prefix web run lint
npm --prefix web run build
```

真实 Graph RAG 全链路需要 Java、AI、Celery、PG+AGE、Redis、bge-m3 和有效
DeepSeek 凭据：

```bash
P9_ADMIN_PASSWORD=你的管理员密码 python scripts/p9_kb_e2e.py
```

## API 验证

| 接口 | 说明 |
|------|------|
| `GET  /api/health` | 健康检查 |
| `GET  /actuator/health` | Spring Actuator 健康 |
| `POST /api/auth/login` | 登录获取 JWT |
| `POST /api/fs/list` | 文件列表（支持目录密码、隐藏过滤、README/Header） |
| `POST /api/fs/get` `dirs` `mkdir` `rename` `move` `copy` `remove` | 文件读写操作 |
| `PUT  /api/fs/put` | 流式上传 |
| `GET  /d/**?sign=` | 文件下载（Range；密码目录需签名） |
| `POST /api/fs/search` | 文件名搜索（按权限/basePath 过滤，分页） |
| `GET  /api/task/list` `{id}` `{id}/cancel` | 任务中心（查询/详情/取消） |
| `GET/POST /api/admin/meta/*` | 目录 Meta 规则管理（list/get/create/update/delete） |
| `POST /api/admin/index/build` `update` | 文件名索引重建（异步任务）/ 子树更新 |
| `GET/POST /api/admin/storage/*` `driver/*` | 存储与驱动管理 |
| `GET/POST /api/admin/user/*` `role/*` | 用户与角色管理 |
| `GET/POST /api/kb` | 知识库列表与创建 |
| `GET/POST /api/kb/{kbId}/documents` | 文档列表、加入知识库与索引状态 |
| `POST /api/kb/{kbId}/chat` | 整库 Agentic RAG 问答（SSE） |
| `POST /api/kb/{kbId}/documents/{docId}/chat` | 单文档问答（SSE） |

Python `/kb/**` 仅供 Java 内网调用，不直接暴露给用户前端。

## 文档

| 文档 | 内容 |
|------|------|
| [docs/overview-design.md](docs/overview-design.md) | 概要设计 |
| [docs/detailed-design.md](docs/detailed-design.md) | 详细设计（领域模型、接口、缓存、DB） |
| [docs/development-plan.md](docs/development-plan.md) | 阶段开发计划（M0-M9） |
| [docs/2026-06-10-agentic-graph-rag-kb.md](docs/2026-06-10-agentic-graph-rag-kb.md) | Graph RAG 知识库设计 |
| [docs/2026-06-11-p9-test-acceptance.md](docs/2026-06-11-p9-test-acceptance.md) | P9 测试与验收 |
| [AGENTS.md](AGENTS.md) | AI 工作规范 |

## 开发阶段

| 阶段 | 状态 | 目标 |
|------|------|------|
| M0 | ✅ | 工程治理、docker-compose、本地基线 |
| M1 | ✅ | 数据库迁移体系（Flyway + JPA 实体） |
| M2 | ✅ | 认证、JWT、用户角色 |
| M3 | ✅ | 存储挂载与 LocalDriver |
| M4 | ✅ | 文件系统读写闭环 |
| M5 | ✅ | 目录 Meta、隐藏规则、README/Header、下载签名 |
| M6 | ✅ | 任务中心（异步/进度/取消）与文件名索引/搜索 |
| M7 | ✅ | 分享与公开访问（密码/过期/访问次数/阅后即焚/禁下载，分享下载链路） |
| M8 | ✅ | 更多驱动：AWS S3（读写、预签名 302）、百度网盘（读写、代理下载） |
| M9 | ✅ | Graph RAG 知识库、增量索引、整库/单文档问答与质量门禁 |
| WebDAV | ✅ | WebDAV 服务端（Digest + 专用密码），把全部存储挂到 Windows/macOS/rclone |
