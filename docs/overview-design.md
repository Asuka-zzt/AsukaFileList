# AsukaFileList 概要设计

## 1. 背景与目标

AsukaFileList 目标是逐步搭建一个 Java 版本的 AList，并在文件管理能力之上接入 Python RAG 服务，形成“网盘文件列表 + 语义搜索 + 智能知识库问答”的一体化产品。

产品分为两个核心服务：

- Java 主服务：负责用户、权限、存储挂载、统一文件系统、下载/上传、分享、任务、管理后台接口等 AList 核心能力。
- Python AI 服务：负责文件内容解析、文本切分、Embedding、向量检索、混合检索和 RAG 流式问答。

本设计以 `ref/alist` 的源码为主要参考，尤其参考其驱动抽象、挂载路径解析、文件系统编排、权限校验和索引构建机制。

## 2. AList 源码参考结论

| 设计主题 | AList 参考源码 | 可借鉴点 |
| --- | --- | --- |
| HTTP 路由分层 | `ref/alist/server/router.go` | 将公共接口、登录接口、文件系统接口、管理接口、分享接口、任务接口分组管理 |
| 驱动 SPI | `ref/alist/internal/driver/driver.go` | Driver 由 Meta、Reader 组成，写操作、归档、Other 能力用可选接口扩展 |
| 驱动配置描述 | `ref/alist/internal/driver/config.go`, `item.go` | 每个驱动声明名称、是否支持上传、是否本地排序、配置项定义 |
| 存储挂载管理 | `ref/alist/internal/op/storage.go` | 存储配置入库后实例化驱动，初始化成功后维护内存挂载表 |
| 挂载路径解析 | `ref/alist/internal/op/path.go` | 用户请求路径按挂载点匹配为 storage + actualPath |
| 文件系统编排 | `ref/alist/internal/fs/*.go`, `ref/alist/internal/op/fs.go` | fs 层接收挂载路径，op 层调用实际驱动，并统一处理缓存、排序、链接缓存 |
| 文件对象模型 | `ref/alist/internal/model/obj.go`, `object.go`, `args.go` | 统一 Obj、Link、FileStreamer，屏蔽不同云盘差异 |
| 权限模型 | `ref/alist/internal/model/user.go`, `role.go`, `server/common/role_perm.go` | 用户基础路径、角色路径授权、权限位组合、隐藏文件和密码目录 |
| 索引构建 | `ref/alist/internal/search/*.go` | 遍历文件树、批量写入搜索索引、支持重建/更新/停止/进度 |
| 分享模型 | `ref/alist/internal/model/share.go`, `server/handles/share*.go` | 分享链接、密码、过期时间、访问次数、预览/下载控制 |

## 3. 系统边界

### 3.1 Java 主服务职责

- 用户认证：登录、JWT、会话、管理员 token、访客。
- 权限控制：基于用户基础路径、角色路径权限、目录 meta 规则进行访问判断。
- 存储管理：创建、更新、启用、禁用、删除挂载存储。
- 驱动体系：用 Java SPI/接口实现 Local、S3、WebDAV、AList 远程、阿里云盘等驱动的渐进扩展。
- 统一文件系统：提供 list、get、mkdir、rename、move、copy、remove、upload、link/download。
- 分享能力：公开分享、密码校验、过期、访问次数、下载限制。
- 任务中心：上传、复制、离线下载、索引等耗时任务的异步调度和进度查询。
- AI 服务协作：文件新增/更新后触发索引任务，向 Python 服务提供内部下载 URL。

### 3.2 Python AI 服务职责

当前 `ai-service` 已具备雏形：

- FastAPI 服务入口：`ai-service/app/main.py`
- 内部索引接口：`POST /internal/index`
- 语义搜索：`POST /v1/search/semantic`
- 混合搜索：`POST /v1/search/hybrid`
- RAG 问答：`POST /v1/chat`，SSE 流式输出
- Celery 异步索引任务
- PostgreSQL + pgvector 存储文本 chunk 向量
- Redis 作为 Celery broker/result backend

AI 服务不直接管理用户文件系统，只通过 Java 主服务授权的内部下载 URL 拉取文件内容。

## 4. 总体架构

```mermaid
flowchart LR
    Web[Web UI / API Client] --> Java[Java 主服务]
    Admin[Admin Console] --> Java
    WebDav[WebDAV/S3/FTP 客户端] --> Java

    Java --> DB[(MySQL: 用户/权限/存储/分享/任务)]
    Java --> Cache[(Redis: 缓存/锁/任务状态)]
    Java --> Storage[(本地磁盘/对象存储/网盘)]

    Java -->|内部索引任务| AI[Python AI Service]
    AI --> PG[(PostgreSQL + pgvector)]
    AI --> Redis[(Redis/Celery)]
    AI --> LLM[DeepSeek / 其他模型服务]
    AI -->|下载待索引文件| Java
```

## 5. 核心模块划分

### 5.1 Java 主服务模块

| 模块 | 职责 |
| --- | --- |
| auth | 登录、JWT、会话、密码、管理员 token、访客鉴权 |
| user-role | 用户、角色、权限位、路径权限范围 |
| storage | 存储挂载配置、驱动初始化、挂载表维护 |
| driver-spi | 驱动接口、驱动配置描述、可选能力接口 |
| fs | 统一文件系统应用服务，完成路径解析、权限检查、缓存、排序和驱动调用 |
| share | 分享链接、访问令牌、过期和访问限制 |
| task | 异步任务、进度、取消、失败重试 |
| search-index | 文件名索引及和 AI 索引的触发协调 |
| ai-client | 调用 Python AI 服务的内部客户端 |
| protocol | HTTP 下载、WebDAV、S3 兼容协议，后续再扩展 FTP/SFTP |
| admin-api | 管理存储、驱动、用户、角色、设置、索引、任务 |

### 5.2 Python AI 服务模块

| 模块 | 当前代码 | 职责 |
| --- | --- | --- |
| api | `ai-service/app/api` | 暴露索引、搜索、问答、任务状态接口 |
| index | `services/index_service.py` | 下载文件、解析文本、切分、生成向量、入库 |
| embedding | `services/embedding_service.py` | 模型 Embedding API 封装和重试 |
| search | `services/search_service.py` | pgvector 语义搜索、PostgreSQL 全文搜索、RRF 融合 |
| chat | `services/chat_service.py` | 检索上下文并调用 Chat API，SSE 流式输出 |
| task | `tasks/index_tasks.py` | Celery 异步索引任务 |

## 6. 关键业务流程

### 6.1 文件列表

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Java Fs API
    participant Auth as Auth/Permission
    participant FS as FsService
    participant OP as StorageResolver
    participant D as Driver

    C->>API: POST /api/fs/list {path}
    API->>Auth: 校验用户、基础路径、meta 密码、角色权限
    API->>FS: list(mountPath)
    FS->>OP: resolve(mountPath)
    OP-->>FS: storage + actualPath
    FS->>D: list(actualPath)
    D-->>FS: FileObject[]
    FS-->>API: 排序、隐藏过滤、分页、签名
    API-->>C: FsListResp
```

### 6.2 文件上传后触发知识库索引

```mermaid
sequenceDiagram
    participant C as Client
    participant Java as Java 主服务
    participant Task as TaskService
    participant AI as Python AI Service
    participant PG as pgvector

    C->>Java: PUT /api/fs/put
    Java->>Java: 权限校验、路径解析、驱动上传
    Java->>Task: 创建 AI 索引任务事件
    Task->>AI: POST /internal/index
    AI->>Java: GET internal download URL
    AI->>AI: 解析文本、切分、Embedding
    AI->>PG: upsert vector_doc
    AI-->>Task: taskId / task result
```

### 6.3 RAG 问答

```mermaid
sequenceDiagram
    participant C as Client
    participant Java as Java 主服务
    participant AI as Python AI Service
    participant PG as pgvector
    participant LLM as Chat Model

    C->>Java: POST /api/ai/chat
    Java->>Java: 鉴权并补充 userId
    Java->>AI: POST /v1/chat
    AI->>PG: semantic_search(topK)
    AI->>LLM: chat.completions(stream=true)
    LLM-->>AI: token stream
    AI-->>Java: SSE
    Java-->>C: SSE
```

## 7. 数据存储规划

- MySQL：Java 主服务业务数据，包括用户、角色、存储、meta、分享、任务、文件索引元数据。
- Redis：任务队列、短期缓存、分布式锁、会话刷新缓存。
- PostgreSQL + pgvector：AI 服务向量数据和全文检索数据。
- 对象/网盘存储：真实文件内容，由各驱动访问。

## 8. 安全设计原则

- 外部用户只访问 Java 主服务，Python AI 服务只暴露内网或通过 API Key 保护。
- Java 生成内部下载 URL 时必须带短期签名或内部 token。
- Python AI 服务请求 Java 下载文件时使用 `Authorization: Bearer <master_token>`。
- 用户路径必须先经过 `basePath` 拼接和规范化，避免越权访问挂载根以外路径。
- 分享访问令牌和下载签名分离，避免公开分享获得主站登录能力。

## 9. 渐进式交付路线

| 阶段 | 目标 |
| --- | --- |
| M1 | Java Spring Boot 项目骨架、用户/角色/存储表、Local 驱动、基础 list/get/download |
| M2 | 上传、mkdir、rename、move、copy、remove、任务中心、文件名搜索 |
| M3 | Python AI 服务和 Java 联调，上传/更新触发索引，语义搜索和 RAG 问答代理 |
| M4 | 分享、目录 meta、隐藏规则、下载签名、缓存和限速 |
| M5 | WebDAV/S3 协议兼容，更多网盘驱动，归档预览/解压 |
| M6 | 知识库增强：多格式解析、重排、引用溯源、增量索引、权限过滤检索 |

