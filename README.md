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

### 第一步：准备环境变量

```bash
cp .env.example .env
# 编辑 .env，填入 DB_PASSWORD 等真实值
```

### 第二步：启动基础设施

```bash
# 启动 MySQL、PostgreSQL、Redis
docker compose up -d mysql postgres redis

# 等待健康检查通过（约 20 秒）
docker compose ps
```

### 第三步：启动 Java 主服务

```bash
mvn spring-boot:run
```

服务启动后 Flyway 会自动建表。访问：

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

### 第四步（可选）：启动 Python AI 服务

```bash
cd ai-service
pip install -r requirements.txt
uvicorn app.main:app --reload

# 异步任务 worker
celery -A app.core.celery_app worker --loglevel=info
```

### 第五步（可选）：启动 Web 前端（AList 风格）

```bash
cd web
npm install
npm run dev
```

前端默认运行在 **http://localhost:5174** ，开发时代理 `/api` 到后端 8080。

使用 admin 账号登录后即可：

- 浏览虚拟挂载的存储（/api/fs/list）
- 管理存储（需要 admin 权限）

详细设计见 `docs/web-frontend-design.md`。

## 运行测试

```bash
# 单元测试（不需要 MySQL，使用 H2 内存库）
mvn test

# 编译检查
mvn compile -q
```

## API 验证

| 接口 | 说明 |
|------|------|
| `GET  /api/health` | 健康检查 |
| `GET  /actuator/health` | Spring Actuator 健康 |
| `POST /api/fs/list` | 文件列表（当前返回占位数据） |

## 文档

| 文档 | 内容 |
|------|------|
| [docs/overview-design.md](docs/overview-design.md) | 概要设计 |
| [docs/detailed-design.md](docs/detailed-design.md) | 详细设计（领域模型、接口、缓存、DB） |
| [docs/development-plan.md](docs/development-plan.md) | 阶段开发计划（M0–M10） |
| [AGENTS.md](AGENTS.md) | AI 工作规范 |

## 开发阶段

| 阶段 | 状态 | 目标 |
|------|------|------|
| M0 | ✅ | 工程治理、docker-compose、本地基线 |
| M1 | ✅ | 数据库迁移体系（Flyway + JPA 实体） |
| M2 | 计划中 | 认证、JWT、用户角色 |
| M3 | 计划中 | 存储挂载与 LocalDriver |
| M4 | 计划中 | 文件系统读写闭环 |
