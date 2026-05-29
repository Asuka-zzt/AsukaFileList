# AsukaFileList 开发流程与阶段计划

## 需求背景

AsukaFileList 需要从当前 Java Spring Boot 脚手架逐步演进为完整的 AList 风格文件列表系统，并与现有 Python `ai-service` 组合成智能知识库产品。

当前已有基础：

- Java 主服务脚手架：`pom.xml`、启动类、统一响应、统一异常、路径工具、驱动 SPI 骨架、`/api/health`、`/api/fs/list` 占位接口。
- Python AI 服务雏形：FastAPI、Celery、pgvector、语义搜索、混合搜索、RAG 流式问答。
- 设计文档：`docs/overview-design.md`、`docs/detailed-design.md`。

本文件用于约束后续完整开发流程：每个阶段先补设计，再实现，再测试，再进入下一阶段。

## 方案设计

### 总体开发原则

1. 每个功能需求先在 `docs/` 中补充或更新设计，设计通过后再编码。
2. Java 主服务先做可运行闭环，再扩展复杂协议和云盘驱动。
3. Python AI 服务保持独立部署，Java 只通过内部 API 调用。
4. 每个阶段都必须有可验证的 API、测试命令和验收标准。
5. 阶段之间不抢跑：未完成数据模型和权限前，不做公开分享和复杂协议。

### 分支与提交流程

每个里程碑使用独立分支：

```bash
git checkout main
git pull
git checkout -b feat/<milestone-short-name>
```

提交粒度：

- 一个逻辑单元一个 commit。
- 文档更新使用 `docs(scope): ...`。
- 功能实现使用 `feat(scope): ...`。
- 修复使用 `fix(scope): ...`。
- 测试补充使用 `test(scope): ...`。

建议 scope：

- `common`
- `db`
- `auth`
- `storage`
- `fs`
- `driver`
- `ai`
- `share`
- `task`
- `protocol`

### 阶段验收命令

Java 主服务通用命令：

```bash
mvn compile -q
mvn test
mvn spring-boot:run
```

Python AI 服务通用命令：

```bash
cd ai-service
uvicorn app.main:app --reload
celery -A app.core.celery_app worker --loglevel=debug
```

基础设施通用命令：

```bash
docker compose up -d mysql postgres redis
```

## 阶段路线

### M0：工程治理与本地开发基线

目标：让项目在本地可以稳定启动、测试、查看文档，并统一开发规范。

实现步骤：

1. 新增 `docker-compose.yml`：
   - 启动 MySQL、PostgreSQL、Redis。
   - 暴露 Java 主服务和 Python AI 服务需要的端口。
2. 新增 `.env.example` 补全：
   - Java 数据库连接。
   - Redis 地址。
   - AI 服务地址与内部密钥。
3. 新增 `docs/development-plan.md`：
   - 记录阶段计划、验收标准、命令。
4. 更新 `README.md`：
   - 增加本地启动顺序。
   - 增加 API 验证命令。
5. 建立测试策略：
   - 单元测试：工具类、权限规则、路径解析。
   - 集成测试：Controller、数据库迁移、LocalDriver。

验收标准：

- `mvn test` 通过。
- `GET /api/health` 返回成功。
- 文档能指导新开发者完成本地启动。

### M1：数据库与迁移体系

目标：建立 Java 主服务业务库模型，为用户、角色、存储、meta、分享、任务打底。

方案设计：

- 使用 Flyway 管理数据库迁移。
- 使用 MySQL 作为主服务业务库。
- 第一阶段只建核心表，不急于实现所有业务。

数据库变更：

1. `users`
2. `roles`
3. `user_roles`
4. `storages`
5. `meta_rules`
6. `shares`
7. `tasks`
8. `file_index_nodes`

实现步骤：

1. 修改 `pom.xml`：
   - 增加 `spring-boot-starter-data-jpa`。
   - 增加 `mysql-connector-j`。
   - 增加 `flyway-core`、`flyway-mysql`。
2. 新增 `src/main/resources/db/migration/V1__init_schema.sql`：
   - 创建核心表和索引。
3. 新增 `src/main/java/com/asuka/filelist/infrastructure/persistence/entity/*Entity.java`：
   - `UserEntity`
   - `RoleEntity`
   - `StorageEntity`
   - `MetaRuleEntity`
   - `ShareEntity`
   - `TaskEntity`
   - `FileIndexNodeEntity`
4. 新增 repository：
   - `UserRepository`
   - `RoleRepository`
   - `StorageRepository`
   - `MetaRuleRepository`
   - `ShareRepository`
   - `TaskRepository`
   - `FileIndexNodeRepository`
5. 修改 `application.yml`：
   - 增加 `spring.datasource`。
   - 增加 `spring.jpa`。
   - 增加 `spring.flyway`。
6. 新增数据库集成测试：
   - 验证 Flyway 可执行。
   - 验证 repository 基础 CRUD。

异常处理：

| 场景 | 错误码 |
| --- | --- |
| 数据库连接失败 | `INTERNAL_ERROR` |
| 唯一键冲突 | `BAD_REQUEST` |
| 实体不存在 | `OBJECT_NOT_FOUND` |

验收标准：

- `mvn test` 通过。
- 启动服务时 Flyway 自动建表。
- MySQL 中能看到核心表。

### M2：认证、用户与角色权限

目标：实现登录、JWT、当前用户、角色路径权限，为文件系统访问控制打底。

接口签名：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 用户登录 |
| `GET` | `/api/me` | 当前用户 |
| `POST` | `/api/me/update` | 更新当前用户基础信息 |
| `GET` | `/api/auth/logout` | 登出 |
| `GET` | `/api/admin/user/list` | 用户列表 |
| `POST` | `/api/admin/user/create` | 创建用户 |
| `GET` | `/api/admin/role/list` | 角色列表 |
| `POST` | `/api/admin/role/create` | 创建角色 |

实现步骤：

1. 新增 `domain/user`：
   - `User`
   - `Role`
   - `PermissionScope`
   - `PermissionBits`
2. 新增 `application/auth`：
   - `AuthService`
   - `PasswordService`
   - `TokenService`
3. 新增 `infrastructure/security`：
   - `JwtTokenProvider`
   - `AuthenticationInterceptor`
   - `CurrentUser`
4. 新增 `api/controller/AuthController.java`。
5. 新增 `api/controller/UserController.java`。
6. 新增 `api/controller/RoleController.java`。
7. 新增初始化逻辑：
   - 首次启动创建 admin 用户和 guest 用户。
8. 增加测试：
   - 密码 hash 不可逆。
   - JWT 过期和密码时间戳失效。
   - 角色权限合并。
   - 用户基础路径拼接。

安全考量：

- 密码使用强 hash，不存明文。
- JWT secret 必须来自环境变量。
- 前端传入的 `userId` 不可信，所有用户身份从 token 获取。
- 登出后 token 加入 Redis 黑名单，M2 可先预留接口，M4 接 Redis 后完成。

验收标准：

- 能创建用户和角色。
- 登录后可以访问 `/api/me`。
- 无 token 访问受保护接口返回 401。

### M3：存储挂载与 LocalDriver

目标：实现 AList 风格的存储挂载表、驱动注册、路径解析和本地文件驱动。

接口签名：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/driver/list` | 驱动信息列表 |
| `GET` | `/api/admin/driver/names` | 驱动名称列表 |
| `GET` | `/api/admin/storage/list` | 存储列表 |
| `POST` | `/api/admin/storage/create` | 创建存储 |
| `POST` | `/api/admin/storage/update` | 更新存储 |
| `POST` | `/api/admin/storage/enable` | 启用存储 |
| `POST` | `/api/admin/storage/disable` | 禁用存储 |
| `POST` | `/api/admin/storage/delete` | 删除存储 |

核心流程：

1. 管理员创建 storage。
2. storage 配置写入数据库。
3. `StorageRuntimeService` 根据 driver 名称创建驱动。
4. 驱动读取 `addition` 配置并执行 `init`。
5. 初始化成功后注册到 `MountedStorageRegistry`。
6. 文件访问时按最长挂载路径匹配 storage。

实现步骤：

1. 完善 `infrastructure/driver`：
   - `DriverInfo`
   - `DriverItem`
   - `DriverWriter`
   - `UploadFile`
   - `ProgressListener`
2. 新增 `infrastructure/driver/local`：
   - `LocalDriver`
   - `LocalDriverAddition`
   - `LocalDriverFactory`
3. 新增 `application/storage`：
   - `StorageApplicationService`
   - `MountedStorageRegistry`
   - `StorageResolver`
   - `ResolvedStoragePath`
4. 新增 `api/controller/AdminStorageController.java`。
5. 新增 `api/controller/AdminDriverController.java`。
6. 增加测试：
   - LocalDriver list/get/link。
   - 挂载路径最长前缀匹配。
   - 禁用 storage 后不可访问。

异常处理：

| 场景 | 错误码 |
| --- | --- |
| 驱动不存在 | `BAD_REQUEST` |
| 挂载路径冲突 | `BAD_REQUEST` |
| 根目录不存在 | `BAD_REQUEST` |
| 存储未初始化 | `STORAGE_NOT_FOUND` |

验收标准：

- 能创建 Local 存储。
- `/api/admin/driver/list` 能看到 Local 驱动配置。
- `/api/fs/list` 可以列出本地目录。

### M4：文件系统读写闭环

目标：完成核心文件管理能力，形成可用的 AList Java 版本最小闭环。

接口签名：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/fs/list` | 文件列表 |
| `POST` | `/api/fs/get` | 文件详情 |
| `POST` | `/api/fs/dirs` | 目录列表 |
| `POST` | `/api/fs/mkdir` | 新建目录 |
| `POST` | `/api/fs/rename` | 重命名 |
| `POST` | `/api/fs/move` | 移动 |
| `POST` | `/api/fs/copy` | 复制 |
| `POST` | `/api/fs/remove` | 删除 |
| `PUT` | `/api/fs/put` | 流式上传 |
| `PUT` | `/api/fs/form` | 表单上传 |
| `GET` | `/d/**` | 下载 |

核心流程：

1. Controller 参数校验。
2. 从 token 获取当前用户。
3. `user.basePath + request.path` 得到实际访问路径。
4. 校验角色权限和 meta 规则。
5. `StorageResolver` 解析 storage 和 actualPath。
6. 调用驱动读写能力。
7. 清理缓存并发布文件变更事件。

实现步骤：

1. 完善 `FsApplicationService`：
   - `list`
   - `get`
   - `dirs`
   - `mkdir`
   - `rename`
   - `move`
   - `copy`
   - `remove`
   - `put`
   - `link`
2. 新增请求响应 DTO：
   - `FsGetRequest`
   - `FsMkdirRequest`
   - `FsRenameRequest`
   - `FsMoveRequest`
   - `FsCopyRequest`
   - `FsRemoveRequest`
3. 新增 `DownloadController.java`：
   - 处理 `/d/**`。
   - 支持 Range。
4. 新增 `FileTypeUtils`：
   - MIME 推断。
   - 文件类型枚举。
5. 新增 `FileEventPublisher`：
   - `FileCreated`
   - `FileUpdated`
   - `FileMoved`
   - `FileDeleted`
6. 增加测试：
   - 路径穿越拒绝。
   - 无权限写入拒绝。
   - LocalDriver 上传下载闭环。
   - Range 下载。

性能考量：

- 列表结果做短期缓存。
- 下载链接做过期缓存。
- 大文件上传进入任务中心。
- 跨存储 copy 走任务。

验收标准：

- 本地目录可完整 list/get/download/upload。
- 权限不足时返回 403。
- 写操作后列表缓存刷新。

### M5：目录 Meta、隐藏规则与下载签名

目标：实现目录密码、隐藏规则、README/Header、下载签名。

接口签名：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/meta/list` | meta 列表 |
| `GET` | `/api/admin/meta/get` | meta 详情 |
| `POST` | `/api/admin/meta/create` | 创建 meta |
| `POST` | `/api/admin/meta/update` | 更新 meta |
| `POST` | `/api/admin/meta/delete` | 删除 meta |

实现步骤：

1. 新增 `domain/meta/MetaRule.java`。
2. 新增 `application/meta/MetaApplicationService.java`。
3. 新增 `api/controller/AdminMetaController.java`。
4. 新增 `DownloadSignService`：
   - HMAC 签名。
   - 过期时间。
   - 路径绑定。
5. 在 `FsApplicationService.list` 中加入隐藏过滤。
6. 在 `DownloadController` 中验证下载签名。
7. 增加测试：
   - meta 最近路径匹配。
   - hide 正则过滤。
   - sign 过期。
   - sign 路径不匹配。

验收标准：

- 设置目录密码后，未提供密码无法访问。
- 隐藏规则对普通用户生效。
- 需要签名的文件无 sign 不可下载。

### M6：任务中心与文件名索引

目标：支持长耗时任务，并建立文件名搜索能力。

接口签名：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/task/list` | 任务列表 |
| `GET` | `/api/task/{id}` | 任务详情 |
| `POST` | `/api/task/{id}/cancel` | 取消任务 |
| `POST` | `/api/admin/index/build` | 重建文件名索引 |
| `POST` | `/api/admin/index/update` | 更新指定路径索引 |
| `POST` | `/api/fs/search` | 文件名搜索 |

实现步骤：

1. 新增 `application/task`：
   - `TaskApplicationService`
   - `TaskExecutor`
   - `TaskProgress`
2. 新增任务类型：
   - `UploadTask`
   - `CopyTask`
   - `BuildFileIndexTask`
3. 新增 `application/search`：
   - `FileNameIndexService`
   - `FileTreeWalker`
4. 写入 `file_index_nodes`。
5. `FileEventPublisher` 触发增量索引。
6. 增加测试：
   - 任务状态流转。
   - 重建索引。
   - 搜索分页。

验收标准：

- 大文件上传可以作为任务执行。
- `/api/fs/search` 能按文件名搜索。
- 重建索引可查看进度。

### M7：Python AI 服务集成

目标：Java 主服务与 Python RAG 服务联调，实现语义搜索和问答代理。

接口签名：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/ai/index/file` | 手动触发索引 |
| `POST` | `/api/ai/search/semantic` | 语义搜索 |
| `POST` | `/api/ai/search/hybrid` | 混合搜索 |
| `POST` | `/api/ai/chat` | RAG 问答 SSE |
| `GET` | `/api/ai/tasks/{taskId}` | AI 任务状态 |
| `GET` | `/internal/files/{userFileId}/download` | AI 内部下载 |

核心流程：

1. 用户上传或更新文件。
2. Java 记录文件元数据，生成 `userFileId`。
3. Java 生成短期内部下载 URL。
4. Java 调用 Python `POST /internal/index`。
5. Python 下载文件、解析、切分、写入 pgvector。
6. 用户发起语义搜索或问答。
7. Java 代理请求 Python，并对结果做权限二次校验。

实现步骤：

1. 修改 `pom.xml`：
   - 增加 HTTP client 依赖，优先使用 Spring `RestClient` 或 `WebClient`。
2. 替换 `NoopAiServiceClient`：
   - 实现 `HttpAiServiceClient`。
   - 支持 API Key。
   - 设置连接和读取超时。
3. 新增 `api/controller/AiController.java`。
4. 新增 `api/controller/InternalFileController.java`。
5. 新增 `domain/fs/UserFile.java` 或 `file_records` 表：
   - 绑定 `userFileId`、path、storageId、mimeType、hash。
6. Python AI 服务补充：
   - 删除索引接口。
   - 更新索引元数据字段。
   - 搜索结果带 `path/fileName/mimeType`。
7. 增加测试：
   - AI client mock。
   - 内部下载 token 校验。
   - 搜索结果权限过滤。
   - SSE 代理。

安全考量：

- `/internal/**` 不对公网暴露。
- 内部下载 URL 必须短期有效。
- Python 返回的 `userFileId` 必须由 Java 再查一次权限。

验收标准：

- 上传 PDF 后可以触发 AI 索引。
- `/api/ai/search/hybrid` 返回相关 chunk。
- `/api/ai/chat` 可以流式回答。

### M8：分享、预览与公开访问

目标：实现公开分享链接、密码、过期、访问次数和下载控制。

接口签名：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/share/create` | 创建分享 |
| `POST` | `/api/share/update` | 更新分享 |
| `POST` | `/api/share/delete` | 删除分享 |
| `GET` | `/api/share/list` | 我的分享 |
| `GET` | `/api/public/share/info` | 公开分享信息 |
| `POST` | `/api/public/share/auth` | 分享密码校验 |
| `POST` | `/api/public/share/list` | 分享目录列表 |
| `POST` | `/api/public/share/get` | 分享文件详情 |
| `GET` | `/sd/{shareId}/**` | 分享下载 |

实现步骤：

1. 新增 `domain/share/Share.java`。
2. 新增 `application/share/ShareApplicationService.java`。
3. 新增 `ShareTokenService`。
4. 新增 `ShareController.java`。
5. 新增 `PublicShareController.java`。
6. 分享下载复用 `FsApplicationService.link`。
7. 增加测试：
   - 密码分享。
   - 过期分享。
   - 阅后即焚。
   - 禁止下载。

验收标准：

- 可创建公开分享。
- 分享密码正确后可访问。
- 过期和访问次数限制生效。

### M9：协议兼容与更多驱动

目标：扩展 WebDAV、S3 兼容协议和更多存储驱动。

优先级：

1. WebDAV 读写。
2. S3 兼容只读。
3. S3 兼容读写。
4. WebDAV 远程驱动。
5. S3 远程驱动。
6. Alist v3 远程驱动。
7. 阿里云盘、OneDrive 等云盘驱动。

实现步骤：

1. 新增 `infrastructure/protocol/webdav`。
2. 新增 `infrastructure/protocol/s3`。
3. 复用 `FsApplicationService` 做协议后端。
4. 增加协议权限位校验：
   - WebDAV read/write。
   - S3 read/write。
5. 每个驱动独立测试：
   - 配置解析。
   - list/get/link。
   - 上传下载。
   - 错误映射。

验收标准：

- WebDAV 客户端可挂载并浏览 Local 存储。
- S3 兼容客户端可列 bucket/object。

### M10：前端控制台与用户体验

目标：提供可用的 Web 控制台，覆盖文件浏览、上传、分享、搜索和 AI 问答。

页面规划：

1. 登录页。
2. 文件浏览页。
3. 上传任务面板。
4. 分享管理页。
5. AI 搜索页。
6. RAG 问答页。
7. 管理后台：
   - 用户管理。
   - 角色管理。
   - 存储管理。
   - Meta 管理。
   - 索引管理。
   - 系统设置。

实现方式：

- 如果后续选择 Vue/React，前端放在 `web/`。
- Java 主服务只提供 API，不在 Controller 中混写页面逻辑。
- 前端静态资源可在生产构建后由 Java 或独立 Nginx 托管。

验收标准：

- 普通用户可以完成浏览、上传、下载、分享、AI 问答。
- 管理员可以完成存储和权限配置。

## 横向能力建设

### 错误码规划

`ErrorCode` 后续扩展为稳定错误码表：

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `BAD_REQUEST` | 400 | 参数错误 |
| `UNAUTHORIZED` | 401 | 未认证 |
| `PERMISSION_DENIED` | 403 | 无权限 |
| `OBJECT_NOT_FOUND` | 404 | 文件或实体不存在 |
| `STORAGE_NOT_FOUND` | 404 | 存储不存在 |
| `DRIVER_NOT_SUPPORTED` | 405 | 驱动不支持 |
| `TASK_NOT_FOUND` | 404 | 任务不存在 |
| `AI_SERVICE_ERROR` | 502 | AI 服务错误 |
| `INTERNAL_ERROR` | 500 | 服务内部错误 |

### 测试策略

| 测试类型 | 覆盖范围 |
| --- | --- |
| 单元测试 | path、permission、sign、driver config、hash |
| Repository 测试 | Flyway、JPA repository、唯一约束 |
| Service 测试 | AuthService、StorageResolver、FsApplicationService |
| Controller 测试 | 参数校验、错误码、鉴权 |
| 集成测试 | LocalDriver 上传下载闭环、AI client mock |
| 端到端测试 | 启动 Java + Python + DB 后跑核心流程 |

### 安全基线

1. 所有用户路径都必须经过规范化和越权校验。
2. 外部接口不允许信任前端传入的 `userId`。
3. 下载签名必须绑定 path 和过期时间。
4. 内部 AI 下载接口必须独立 token 鉴权。
5. 分享 token 和登录 token 分离。
6. 上传文件必须校验大小、扩展名、MIME 和 magic bytes。
7. 管理接口必须要求 admin 权限。

### 性能基线

1. 文件列表缓存按 storage + actualPath 维度管理。
2. 下载链接缓存按 path + IP 策略管理。
3. 跨存储 copy 作为异步任务执行。
4. AI 索引任务异步执行，避免阻塞上传请求。
5. RAG 检索增加 topK 限制和超时控制。
6. 大目录分页返回，默认 `perPage=200`，最大 `500`。

## 实现步骤总览

建议执行顺序：

1. M0 工程治理与本地开发基线。
2. M1 数据库与迁移体系。
3. M2 认证、用户与角色权限。
4. M3 存储挂载与 LocalDriver。
5. M4 文件系统读写闭环。
6. M5 目录 Meta、隐藏规则与下载签名。
7. M6 任务中心与文件名索引。
8. M7 Python AI 服务集成。
9. M8 分享、预览与公开访问。
10. M9 协议兼容与更多驱动。
11. M10 前端控制台与用户体验。

每个阶段完成后必须更新：

- `README.md`
- `docs/detailed-design.md`
- 本阶段新增或变更的专题设计文档
- 对应测试用例

## 下一步建议

下一阶段从 M0 开始，先补齐 `docker-compose.yml`、数据库连接配置和本地启动文档。完成后再进入 M1 的 Flyway 与实体建模。

