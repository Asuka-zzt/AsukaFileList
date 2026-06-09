# M6 任务中心与文件名索引设计

> 状态：已实现并测试通过。
> 分支：`feat(task)/m6-task-index`（从 M5 基线切出）。

## 1. 解决的问题

M5 完成后系统是一个具备目录治理的网盘，但所有操作都是**同步阻塞**的，且无法**按文件名检索**。M6 引入：

1. **任务中心**：长耗时操作（索引重建、未来的大文件上传/跨存储复制）异步执行，可查询进度、可取消。
2. **文件名索引**：将挂载存储的文件树写入 `file_index_nodes`，提供 `/api/fs/search` 全局文件名搜索；写操作后增量维护索引。

不在本阶段范围：分享（M7）、更多驱动/协议（M8）、AI 语义检索（M9）。

## 2. 为什么这样选（关键约束）

- **不引入新依赖**：项目当前**无 Redis、无消息队列、无线程池配置**。按"不新增依赖需先批准"原则，M6 用 **Spring `@Async` + `ThreadPoolTaskExecutor`**（JDK 线程池）做进程内异步，任务状态持久化到既有 `tasks` 表。不接 Redis 分布式队列。
- **复用既有表**：`tasks`、`file_index_nodes` 表与实体/Mapper 在 M1 已建，本阶段仅填充与查询。
- **事件解耦增量索引**：M4 预留了"写操作后发事件"的位点但未实现。M6 用 **Spring `ApplicationEventPublisher`（内置，无新依赖）** 发布 `FileChangeEvent`，由索引监听器异步消费，避免把索引逻辑塞进 `FsApplicationService`。
- **沿用分层**：`TaskController/AdminIndexController/FsController → TaskApplicationService/FileNameIndexService → Mapper`，与 storage/meta 模块同构。

### 单实例限制（已知并接受）

进程内线程池意味着任务**不跨实例、不持久排队**：重启后 `RUNNING/PENDING` 任务会丢失执行线程。处理方式：启动时把残留的 `RUNNING/PENDING` 标记为 `FAILED(stale)`。分布式任务队列留待引入 Redis 时（需单独批准）。

## 3. 总体架构

```
任务:   TaskController(/api/task/**) ─► TaskApplicationService ─┐
        AdminIndexController(/api/admin/index/**) ──────────────┤
                                                                ▼
                                            TaskExecutor (@Async ThreadPoolTaskExecutor)
                                              │ 状态/进度持久化 → tasks 表
                                              ▼
                                            TaskRunnable: BuildFileIndexTask 等

搜索:   FsController(/api/fs/search) ─► FileNameIndexService.search ─► file_index_nodes
                                          (权限 + basePath 过滤 + 分页)

增量:   FsApplicationService 写操作 ─► ApplicationEventPublisher(FileChangeEvent)
                                          ▼ @EventListener(@Async)
                                       FileIndexEventListener ─► FileNameIndexService.apply*
```

新增/变更模块：

```
config/
  AsyncConfig.java              (新) @EnableAsync + ThreadPoolTaskExecutor("taskExecutor")
domain/task/
  Task.java / TaskStatus.java   (新) 领域模型与状态枚举
application/task/
  TaskApplicationService.java   (新) create/list/get/cancel + 启动期 stale 回收
  TaskExecutor.java             (新) 提交 Runnable、状态机、进度节流持久化、取消标志
  TaskProgress.java             (新) 任务内进度回报 + 取消检查回调
  BuildFileIndexTask.java       (新) 索引重建任务体
application/search/
  FileNameIndexService.java     (新) build/update/remove + search
  FileTreeWalker.java           (新) 通过 DriverReader.list 递归遍历存储
infrastructure/event/
  FileChangeEvent.java          (新) created/updated/moved/deleted + storageId/path
  FileIndexEventListener.java   (新) 异步消费事件做增量索引
api/controller/
  TaskController.java           (新) /api/task/list|{id}|{id}/cancel
  AdminIndexController.java      (新) /api/admin/index/build|update
  FsController.java             (改) 新增 /api/fs/search
api/request|response/
  FsSearchRequest / TaskResponse / SearchResultResponse / IndexBuildRequest (新)
application/fs/
  FsApplicationService.java     (改) 写操作发布 FileChangeEvent
db/migration/
  V2__file_index_unique.sql     (新) 给 file_index_nodes 加唯一键 (storage_id,parent,name) 支持幂等增量
```

## 4. 实现方式（分提交批次）

**批次 1 `feat(task)`：任务框架**
- `AsyncConfig`（线程池：核心 2 / 最大 4 / 队列 100，可配 `asuka.task.*`）。
- `Task`/`TaskStatus`、`TaskApplicationService`（持久化 + 所有权校验）、`TaskExecutor`（状态机 + 取消标志 `ConcurrentHashMap<Long,AtomicBoolean>` + 进度节流）、`TaskProgress`。
- 启动期 stale 回收（`ApplicationRunner`）。
- `TaskController` + `TaskResponse`。单测：状态流转、取消、所有权 403。

**批次 2 `feat(search)`：文件名索引 + 搜索**
- `V2` 迁移加唯一键；`FileTreeWalker`、`FileNameIndexService.build/search`。
- `BuildFileIndexTask` 接入任务框架；`AdminIndexController.build`（admin，返回 taskId）。
- `FsController.search` + `FsSearchRequest`/`SearchResultResponse`：按 name 模糊匹配，映射回可见路径，按用户权限 + basePath 过滤，分页。
- 单测：build 后可搜索、分页、权限过滤、跨用户 basePath 隔离。

**批次 3 `feat(search)`：增量索引**
- `FileChangeEvent` + `FsApplicationService` 写操作（mkdir/put/rename/move/copy/remove）发布事件。
- `FileIndexEventListener`（`@Async`）消费做增量 upsert/delete。
- `AdminIndexController.update`（按路径子树重建）。
- 单测：上传后索引出现、删除后索引消失、重命名后名称更新。

**批次 4 `test`/`feat(web)`/`docs`：测试 + 前端 + 文档**
- 前端：任务进度面板（列表 + 进度条 + 取消）、顶部搜索框 + 结果页（分页）。
- 更新 README、`development-plan.md`，回填本文 §8。

> 说明：`UploadTask`/`CopyTask` 在本阶段**仅预留任务类型枚举与框架接口，不落地具体实现**——当前仅 Local 驱动、同存储 copy 为快速同步操作，大文件异步上传需缓冲落盘改造，价值/复杂度不成正比。待 M8 多驱动/跨存储就绪后再补，框架已为其预留扩展点。（见 §7，可在批准时调整范围。）

## 5. 关键接口与数据流

### 接口签名（HTTP）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/task/list` | 登录 | 我的任务（分页/按状态过滤）|
| `GET` | `/api/task/{id}` | 所有者 | 任务详情 |
| `POST` | `/api/task/{id}/cancel` | 所有者 | 取消任务 |
| `POST` | `/api/admin/index/build` | admin | 重建文件名索引（异步，返回 taskId）|
| `POST` | `/api/admin/index/update` | admin | 更新指定路径子树索引 |
| `POST` | `/api/fs/search` | 读（+basePath/权限过滤）| 文件名搜索（分页）|

### 任务状态机

```
PENDING ─submit─► RUNNING ─成功─► SUCCESS
                    │ └─异常─► FAILED
                    └─cancel─► CANCELED
PENDING ─cancel─► CANCELED（未起跑直接取消）
进度：TaskProgress.report(pct) 节流持久化（变化≥5% 或 ≥2s 才写库）
取消：协作式——长任务循环内 checkCanceled() 命中即中止
```

### 文件名索引数据流

```
build(storageId):
  1. 标记 BUILD_INDEX 任务 RUNNING
  2. FileTreeWalker 递归 driver.list(storage 根)
  3. 删除该 storage 旧节点 → 批量 insert 新节点(parent,name,is_dir,size,storage_id)
  4. 进度 = 已遍历目录 / 估算总数；完成置 SUCCESS

search(keyword,page,perPage):
  1. file_index_nodes WHERE name LIKE %kw% （分页）
  2. 每条 → 可见路径 = toVisiblePath(mountPath + parent + name, user.basePath)
  3. resolvePermission != 0 才保留；拼 SearchResultResponse(path,name,isDir,size)
```

### 增量索引数据流

```
FsApplicationService.put/mkdir/rename/move/copy/remove
  └─ publish FileChangeEvent(type, storageId, parent, name, isDir, size)
       └─ @Async FileIndexEventListener
            created/updated → upsert 节点（唯一键 storage_id,parent,name）
            deleted         → 删除节点（目录则删子树）
            moved/renamed   → 删旧 + 插新
```

## 6. 异常处理

| 场景 | 错误码 |
| --- | --- |
| 任务不存在 | `TASK_NOT_FOUND`（新，404）|
| 访问/取消他人任务 | `PERMISSION_DENIED` |
| 已结束任务再取消 | `BAD_REQUEST` |
| 搜索参数非法（空关键字/分页越界）| `BAD_REQUEST` |
| 索引构建中驱动 I/O 失败 | 任务置 `FAILED` 并记 error，不抛给调用方 |
| 非 admin 调 `/api/admin/index/**` | `PERMISSION_DENIED`（拦截器）|

## 7. 风险、限制、后续

- **限制**：进程内任务，不跨实例、重启丢失（启动期标记 stale）。分布式队列待 Redis（需批准）。
- **限制**：`UploadTask/CopyTask` 本阶段不落地（仅框架预留），大文件仍走 M4 的 `>100MB 拒绝`路径直到 M8。
- **限制**：索引为最终一致（增量异步）；搜索结果做二次权限校验，索引滞后只影响"新文件稍晚可被搜到"。
- **风险**：大目录全量 build 耗时/内存——分批 insert + 进度回报缓解；超大存储后续可加分页遍历。
- **后续**：搜索接入 M5 隐藏过滤、索引接入 M9 AI 索引触发、任务重试策略。

## 8. 测试（已完成）

实现拆为 4 个提交批次：任务框架 → 索引/搜索 → 增量索引 → 前端/文档。

| 类型 | 测试 | 位置 |
| --- | --- | --- |
| 集成 | 任务流转 SUCCESS、失败记错、协作式取消、列表/详情/所有权 403、终态取消 400 | `TaskFrameworkTest` |
| 集成 | build（异步任务）→ search 闭环、`.txt` 多命中、非 admin build 403、basePath 边界 | `TaskIndexControllerTest` |
| 集成 | 上传/删除/重命名经异步事件增量维护索引；`/index/update` 重建子树（含绕过 API 落盘文件）| `IncrementalIndexTest` |

执行结果：`mvn test` 全绿（56 个，较 M5 +8）；`npm --prefix web run build` 通过。

> 测试隔离注意：多个 `@SpringBootTest` 共享同一 H2 实例，写操作会经增量事件向 `file_index_nodes` 落数据，故搜索类断言使用全局唯一关键字（如 `qq6`/`ww6`）避免跨类污染。

后续可补：纯单元的 `FileTreeWalker`/进度节流测试、并发任务压力测试。

验收标准（已达成）：`/api/admin/index/build` 异步执行且可查进度；`/api/fs/search` 按文件名分页且仅含有权限项、受 basePath 边界约束；写操作后增量索引生效；`mvn test` 全绿；前端任务面板（进度/取消/重建）与搜索页可用。
