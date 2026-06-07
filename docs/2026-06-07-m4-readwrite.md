# M4 文件系统读写闭环设计

> 状态：待批准。批准后再进入编码。
> 分支：`feat(fs)/m4-readwrite-loop`（实现阶段从 main 切出）。

## 1. 解决的问题

M3 完成后系统只能**列目录（list）**，无法获取文件详情、上传、下载、增删改移。M4 补齐文件管理核心闭环，使 AsukaFileList 成为一个**可用的 Java 版 AList 最小网盘**，并在 `web/` 同步文件管理 UI。

不在本阶段范围（按修订后路线后置）：目录密码/隐藏（M5）、异步任务/大文件分片/跨存储 copy（M6）、分享（M7）、AI 索引（M9）。本阶段 copy/move 仅支持**同存储**同步实现；跨存储与大文件留到 M6 任务中心。

## 2. 为什么这样选

- **复用现有分层**：沿用 `FsController → FsApplicationService → StorageDriver` 调用链，不引入新抽象。
- **驱动只读 → 读写**：当前 `StorageDriver extends DriverMeta, DriverReader`。新增 `DriverWriter` 可选接口，驱动按需实现；不支持写的驱动抛 `DRIVER_NOT_SUPPORTED`，与现有 `DriverGetter instanceof` 判定风格一致。
- **权限位已就绪**：`PermissionBits` 已定义 `WRITE_UPLOAD/RENAME/MOVE/COPY/REMOVE`，直接复用 `PermissionApplicationService.resolvePermission` 做按操作校验，无需改权限模型。
- **下载走独立控制器**：`/d/**` 与 `/api/fs/**` 职责分离，便于后续 M5 接下载签名、M7 接分享下载。
- **不加新依赖**：下载 Range、流式上传均用 Spring MVC 原生 `Resource`/`HttpServletRequest` 实现。

## 3. 总体架构

```
写操作:  FsController ──► FsApplicationService ──► StorageResolver ──► MountedStorageRuntime.driver()
            (参数校验)        (权限位校验 + 路径解析)                          │
                                                                          DriverWriter (LocalDriver)
下载:    DownloadController(/d/**) ──► FsApplicationService.link() ──► DriverReader.link() ──► FileLink
```

新增/变更模块：

```
infrastructure/driver/
  DriverWriter.java        (新) mkdir/move/copy/rename/remove/put
  UploadFile.java          (新) 上传流封装：inputStream/size/mimeType/name
  local/LocalDriver.java   (改) 实现 DriverWriter
api/controller/
  FsController.java        (改) 新增 get/dirs/mkdir/rename/move/copy/remove/put
  DownloadController.java  (新) /d/** + Range
api/request/
  FsGetRequest / FsDirsRequest / FsMkdirRequest / FsRenameRequest
  FsMoveRequest / FsCopyRequest / FsRemoveRequest        (新)
application/fs/
  FsApplicationService.java (改) 新增 get/dirs/mkdir/rename/move/copy/remove/put/link
common/util/
  FileTypeUtils.java       (新) MIME 推断
```

## 4. 实现方式（分提交批次）

> 每批一个或多个 commit，逐步可编译可测。

**批次 1 `feat(driver)`：驱动写能力**
- `DriverWriter`：`mkdir(ctx,parentDir,name)`、`move(ctx,src,dstDir)`、`copy(ctx,src,dstDir)`、`rename(ctx,src,newName)`、`remove(ctx,obj)`、`put(ctx,parentDir,UploadFile)`。
- `UploadFile` record：`name / size / mimeType / InputStream`。
- `LocalDriver` 实现以上方法，全部经 `PathUtils.sanitizePath` + 白名单校验，禁止越出 root。

**批次 2 `feat(fs)`：下载链路**
- `DownloadController` 处理 `GET /d/**`：解析路径 → `FsApplicationService.link` → 回写文件流，支持 `Range`（206 Partial Content）。
- `FileTypeUtils` 推断 `Content-Type`。

**批次 3 `feat(fs)`：读操作补全**
- `FsApplicationService.get/dirs`：`get` 返回单文件详情（复用 `DriverGetter`），`dirs` 仅返回子目录。
- 读操作沿用现有 `resolvePermission(...) == 0 → 拒绝` 逻辑。

**批次 4 `feat(fs)`：写操作**
- `mkdir/rename/move/copy/remove/put`：先按操作校验对应权限位，再 `StorageResolver.resolve` 解析，校验跨存储（move/copy 源与目标 storage 必须相同，否则本阶段 `DRIVER_NOT_SUPPORTED` 并提示 M6 支持），最后调 `DriverWriter`。
- 对应 Request DTO + Controller 端点。

**批次 5 `test`/`feat(web)`/`docs`：测试 + 前端 + 文档**
- 单测见 §7；前端文件管理 UI；更新 README API 表与本文 §7。

## 5. 关键接口与数据流

### 接口签名（HTTP）

| 方法 | 路径 | 权限位 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/fs/get` | 读 | 文件/目录详情 |
| `POST` | `/api/fs/dirs` | 读 | 仅子目录列表 |
| `POST` | `/api/fs/mkdir` | `WRITE_UPLOAD` | 新建目录 |
| `POST` | `/api/fs/rename` | `RENAME` | 重命名 |
| `POST` | `/api/fs/move` | `MOVE` | 移动（同存储）|
| `POST` | `/api/fs/copy` | `COPY` | 复制（同存储）|
| `POST` | `/api/fs/remove` | `REMOVE` | 删除 |
| `PUT` | `/api/fs/put` | `WRITE_UPLOAD` | 流式上传 |
| `GET` | `/d/**` | 读 | 下载（Range）|

> 路径一律先 `joinBasePath(currentUser.basePath, request.path)`，所有用户身份取自 token，不信任前端 `userId`。

### 上传数据流（put）

```
1. Controller 取 token 当前用户 + 目标路径 + 文件流
2. FsApplicationService: 校验 WRITE_UPLOAD 权限位
3. 校验文件大小 ≤ 10GB；> 100MB 本阶段直接拒绝并提示走 M6 异步通道（不在 M4 实现分片）
4. StorageResolver.resolve(path) → runtime + actualPath
5. DriverWriter.put(ctx, parentDir, UploadFile)
6. 返回写入后的 FileObjectResponse
```

### 下载数据流（/d/**）

```
1. 解析 /d 后的路径 → joinBasePath → 读权限校验
2. FsApplicationService.link → DriverReader.link → FileLink(本地为本地文件流/路径)
3. 有 Range 头 → 206 + Content-Range；否则 200 全量
4. Content-Type 由 FileTypeUtils 推断
```

## 6. 异常处理

| 场景 | 错误码 |
| --- | --- |
| 参数缺失/非法名 | `BAD_REQUEST` |
| 未认证 | `UNAUTHORIZED` |
| 无对应操作权限位 | `PERMISSION_DENIED` |
| 目标路径无存储 | `STORAGE_NOT_FOUND` |
| 文件/目录不存在 | `OBJECT_NOT_FOUND` |
| 驱动不支持写 / 跨存储 move-copy | `DRIVER_NOT_SUPPORTED` |
| 上传超限（>10GB 或 >100MB 走任务）| `BAD_REQUEST` |
| 驱动 I/O 失败 | `INTERNAL_ERROR` |

> 路径穿越在 `PathUtils.sanitizePath` 拦截，越界统一 `BAD_REQUEST`。

## 7. 风险、限制、后续

- **限制**：M4 的 copy/move 仅同存储、同步执行；大文件（>100MB）暂不支持，跨存储/大文件留 M6。
- **限制**：未做下载签名（M5），`/d/**` 当前依赖登录态读权限；公网分享下载在 M7。
- **风险**：并发写同名文件 — 本阶段以驱动层覆盖/报错策略为准，不做分布式锁（Redis 锁留 M6）。
- **后续**：写操作完成后发布 `FileEventPublisher` 事件（M6 增量索引、M9 AI 索引消费），M4 先预留事件点但不接消费者。

## 8. 测试（实现后回填）

| 类型 | 用例 |
| --- | --- |
| 单元 | 路径穿越被拒；无对应权限位返回 403；mkdir/rename/move/copy/remove 在 LocalDriver 的行为 |
| 单元 | put 上传 + /d 下载闭环；Range 下载返回 206 与正确字节区间 |
| 单元 | 跨存储 move/copy 返回 `DRIVER_NOT_SUPPORTED`；>100MB 上传被拒 |
| Controller | 各端点参数校验与错误码 |

验收标准：本地目录可完整 `list/get/dirs/download/upload/mkdir/rename/move/copy/remove`；无权限 403；穿越被拒；`mvn test` 全绿；前端文件页可上传/下载/删除/新建目录。
