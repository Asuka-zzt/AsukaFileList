# AGENTS.md

## 1. Project Overview

AsukaFileList 是一个文件列表服务，在文件管理之上接入 Python RAG 服务，提供"网盘挂载 + 语义搜索 + 知识库问答"一体化能力。

## 2. Tech Stack

| 层 | 技术 | 版本 |
|----|------|------|
| Java 主服务 | Spring Boot | 3.3.5 |
| Java 运行时 | Java | 17 |
| ORM | Spring Data JPA / MyBatis Plus | — |
| 主库 | MySQL | 8 |
| 缓存/锁 | Redis | — |
| AI 服务 | FastAPI + Celery | 0.111 / 5.4 |
| 向量库 | PostgreSQL + pgvector | — |
| 文档解析 | pypdf / python-docx / openpyxl | — |
| 构建 | Maven | 3.x |

## 3. Architecture

```
com.asuka.filelist
  api/           → Controller、Request、Response（HTTP 入出参）
  application/   → 用例编排（fs、auth、storage、share、task、ai）
  domain/        → 核心模型和业务规则（user、role、storage、fs、share、task、meta）
  infrastructure/→ 外部实现（persistence、cache、driver、security、client）
  common/        → 基础工具（exception、path、result、util）

ai-service/
  app/api/       → FastAPI 路由（index、search、chat）
  services/      → index / embedding / search / chat 服务
  tasks/         → Celery 异步任务
```

主要调用链：

```
Client → FsController → FsApplicationService
       → StorageDriver（DriverReader / DriverGetter）
       → 各驱动实现（Local / S3 / WebDAV …）
```

AI 联动链：

```
Java 上传完成 → TaskService → POST /internal/index（Python）
Python → 内部下载 URL（Java 签名）→ 解析 → pgvector
```

详细时序见 `docs/overview-design.md` § 6。

## 4. Development Rules

> 完整规范见 `docs/rules/coding.md`

- **最小改动**：只改与需求直接相关的代码；重构另开 `refactor/<desc>` 分支。
- **行数上限**：方法 ≤ 80 行（推荐 ≤ 50）；文件 ≤ 500 行（推荐 ≤ 300）。
- **异常**：统一 `throw new BusinessException(ErrorCode.XXX)`，禁止裸 RuntimeException。
- **注释**：所有类、自定义方法、复杂逻辑块必须加注释，禁止无意义描述。
- **I/O**：外部 HTTP / DB 调用必须有超时与错误处理。
- **事务**：DB 操作必须在 `@Transactional` 或 session 块内，不裸调。
- **禁止**：Controller 层写业务逻辑；Domain/Entity 注入 Spring Bean。

## 5. Workflow

1. **接到需求先读文档**：`docs/overview-design.md`、`docs/detailed-design.md`、受影响的源文件。
2. **写设计文档**：在 `docs/` 下新建或更新，包含接口签名、DB 变更、核心流程、异常处理；**写完后等待用户确认**。
3. **切分支实现**：从 main 切 `feat/<desc>` 分支，按设计逐步实现。
4. **边实现边测试**：每步完成后运行编译和受影响模块的单元测试。
5. **Checklist 对照**：完成功能后用 § 4 Checklist 自查。

## 6. Testing & Verification

```bash
mvn compile -q                  # 编译检查
mvn test -pl .                  # 运行全部单元测试
mvn test -Dtest=PathUtilsTests  # 运行单个测试类
mvn spring-boot:run             # 本地启动（端口 8080）
```

验收标准：`mvn compile -q` 无报错；受影响模块测试全绿；新功能有对应测试用例。

## 7. Git & Commit Rules

```bash
git checkout main && git pull
git checkout -b <type>(<scope>)/<short-desc>
git add -p                                    # 交互式暂存，避免无关改动
git commit -m "<type>(<scope>): 简短描述"
git push -u origin <type>(<scope>)/<short-desc>
```

- **type**：`feat` / `fix` / `refactor` / `docs` / `test` / `chore`
- **scope**：`web` / `storage` / `ai` / `common` / `db` / `ci`
- 不得在 main 直接提交；**不得自动 push**，push 前需用户确认。
- PR 描述需说明改了什么、为什么、如何验证。

## 8. Important Constraints

- **安全边界**：Python AI 服务只暴露内网；Java 生成内部下载 URL 必须带短期签名。
- **路径安全**：用户路径必须经 `PathUtils.sanitizePath` 规范化，防止路径穿越。
- **数据库迁移**：新增表/字段必须提供 Flyway 脚本（`V{n}__desc.sql`），不得手动改表结构。
- **不可动文件**：`pom.xml` 中已管理的依赖版本不得单独覆盖；`application.yml` 中敏感 key 通过环境变量注入（见 `.env.example`）。
- **所有权校验**：涉及用户数据的接口必须校验 `userId` 匹配，防止越权。
- **上传限制**：单文件最大 10 GB；超过 100 MB 走异步任务通道。
