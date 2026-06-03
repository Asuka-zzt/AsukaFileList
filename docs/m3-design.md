# M3 设计文档：存储挂载与 LocalDriver

## 1. 范围说明

M3 目标是实现 AList 风格的存储挂载、驱动注册、最长挂载路径匹配和本地文件驱动。该阶段基于当前 M2 认证与管理员接口实现继续推进，所有 `/api/admin/**` 接口沿用 M2 的 admin 鉴权。

本阶段目标：

- 暴露驱动列表和驱动名称列表接口。
- 支持管理员创建、更新、启用、禁用、删除存储挂载。
- 实现 Local 本地文件驱动，支持 `list/get/link`。
- 实现内存挂载表和最长 mountPath 前缀匹配。
- 改造 `/api/fs/list`：根目录返回虚拟挂载项，挂载路径下返回 LocalDriver 文件列表。

本阶段不实现：

- 上传、移动、复制、删除等写操作。这些放到 M4 文件系统读写闭环。
- 下载 Controller 和签名下载。M3 只让 `LocalDriver.link` 返回可用链接模型，HTTP 下载在 M4/M5 接入。
- 缓存、目录 Meta、隐藏规则、下载签名。分别放到后续阶段。
- 多实例挂载表同步。M3 只维护单机内存挂载表。

## 2. 接口签名

### 2.1 驱动管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/driver/list` | 驱动信息列表 |
| `GET` | `/api/admin/driver/names` | 驱动名称列表 |

`GET /api/admin/driver/list` 响应：

```json
[
  {
    "name": "Local",
    "localSort": true,
    "onlyLocal": true,
    "onlyProxy": false,
    "noCache": false,
    "noUpload": false,
    "defaultRoot": "/",
    "checkStatus": true,
    "items": [
      {
        "name": "rootPath",
        "label": "Root path",
        "type": "string",
        "required": true,
        "defaultValue": "",
        "description": "Local filesystem root path"
      }
    ]
  }
]
```

### 2.2 存储管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/storage/list` | 存储列表 |
| `POST` | `/api/admin/storage/create` | 创建存储 |
| `POST` | `/api/admin/storage/update` | 更新存储 |
| `POST` | `/api/admin/storage/enable` | 启用存储 |
| `POST` | `/api/admin/storage/disable` | 禁用存储 |
| `POST` | `/api/admin/storage/delete` | 删除存储 |

`POST /api/admin/storage/create`

```json
{
  "mountPath": "/local",
  "driver": "Local",
  "addition": {
    "rootPath": "/data/asuka-file-list"
  },
  "orderNo": 0,
  "remark": "本地目录",
  "disabled": false
}
```

`POST /api/admin/storage/update`

```json
{
  "id": 1,
  "mountPath": "/local",
  "driver": "Local",
  "addition": {
    "rootPath": "/data/asuka-file-list"
  },
  "orderNo": 0,
  "remark": "本地目录",
  "disabled": false
}
```

启用、禁用、删除请求：

```json
{
  "id": 1
}
```

## 3. 数据库变更

M1 的 `storages` 表已覆盖 M3 需求，本阶段不新增表字段，不新增 Flyway 脚本。

约束：

- `mount_path` 唯一，入库前必须使用 `PathUtils.fixAndCleanPath` 规范化。
- `driver` 必须能在 `StorageDriverRegistry` 中找到。
- `addition` 存储 JSON 字符串；API 入参使用对象，应用层序列化入库。
- `status` 使用 `work / disabled / init_error`。
- 逻辑启停以 `disabled` 字段为准，`status` 作为运行态辅助展示。

## 4. 新增与调整的代码结构

```text
api/controller/
  AdminDriverController.java
  AdminStorageController.java

api/request/
  StorageCreateRequest.java
  StorageUpdateRequest.java
  StorageIdRequest.java

api/response/
  DriverInfoResponse.java
  DriverItemResponse.java
  StorageResponse.java

application/storage/
  StorageApplicationService.java
  MountedStorageRegistry.java
  MountedStorageRuntime.java
  StorageResolver.java
  ResolvedStoragePath.java

infrastructure/driver/
  DriverInfo.java
  DriverItem.java

infrastructure/driver/local/
  LocalDriver.java
  LocalDriverAddition.java
  LocalDriverFactory.java
```

现有类调整：

| 类 | 调整 |
| --- | --- |
| `StorageDriverFactory` | 增加 `DriverInfo info()` 默认或抽象方法，供管理端展示配置项 |
| `StorageDriverRegistry` | 增加 `driverInfos()`、`driverNames()` 稳定排序输出 |
| `FsApplicationService` | 注入 `StorageResolver`，支持虚拟根和真实驱动 list |

## 5. 驱动描述模型

`DriverInfo`：

```java
public record DriverInfo(
    DriverConfig config,
    List<DriverItem> items
) {}
```

`DriverItem`：

```java
public record DriverItem(
    String name,
    String label,
    String type,
    boolean required,
    String defaultValue,
    String description
) {}
```

M3 只定义 `string/boolean/integer` 三类配置项类型；后续网盘驱动再扩展 `password/select/text` 等类型。

## 6. LocalDriver 设计

### 6.1 addition

```json
{
  "rootPath": "/absolute/local/root"
}
```

`LocalDriverAddition`：

```java
public record LocalDriverAddition(String rootPath) {}
```

校验规则：

- `rootPath` 不能为空。
- `rootPath` 必须是绝对路径。
- `rootPath` 必须存在且为目录。
- `rootPath` 必须落在应用配置的本地存储白名单目录下。
- `LocalDriver` 所有实际访问路径必须经过 `Path.normalize()`，且结果必须仍在 `rootPath` 下，防止路径穿越。

白名单配置：

```yaml
asuka:
  storage:
    local-root-whitelist:
      - ${ASUKA_LOCAL_ROOT_WHITELIST:/tmp/asuka-file-list}
```

### 6.2 list

调用链：

```text
FsApplicationService.list
  -> StorageResolver.resolve(path)
  -> LocalDriver.get(context, actualPath)
  -> LocalDriver.list(context, dir, args)
  -> BasicFileObject 列表
  -> FsApplicationService 排序、分页、响应映射
```

排序规则：

- `extractFolder=front`：目录在前。
- `orderBy=name/size/modified`。
- `orderDirection=asc/desc`。
- M3 先在 Java 主服务内排序，后续驱动可根据 `localSort` 决定是否由驱动本地排序。

### 6.3 get

`LocalDriver.get(context, actualPath)`：

- 找不到路径抛 `BusinessException(ErrorCode.OBJECT_NOT_FOUND)`。
- 返回 `BasicFileObject`，其中：
  - `path` 为驱动内 actualPath。
  - `name` 为文件名，根目录名称为 `/`。
  - `id` 可使用 actualPath，后续有 file id 后再替换。

### 6.4 link

`LocalDriver.link(context, file, args)`：

- M3 返回 `FileLink` 中的 `url` 为 `file://` URI。
- 如果目标为目录，抛 `BusinessException(ErrorCode.BAD_REQUEST)`。
- HTTP 下载接口后续阶段不直接暴露 `file://` 给外部用户。

## 7. 挂载运行时设计

`MountedStorageRuntime`：

```java
public record MountedStorageRuntime(
    Storage storage,
    StorageDriver driver
) {}
```

`MountedStorageRegistry`：

- 使用 `ConcurrentHashMap<Long, MountedStorageRuntime>` 按 storageId 保存运行时。
- `reload(Collection<Storage>)`：启动或批量刷新时重建运行时。
- `mount(Storage)`：创建 driver、setStorage、init，成功后写入内存表。
- `unmount(Long storageId)`：调用 driver.drop 并移除运行时。
- `matchLongestPrefix(String path)`：按 `mountPath` 最长前缀匹配。
- `listMounts()`：返回当前已挂载运行时，供虚拟根展示。

最长前缀规则：

- `/` 可匹配所有路径，但如果存在 `/docs/sub`，请求 `/docs/sub/a.txt` 必须优先命中 `/docs/sub`。
- `/docs` 只能匹配 `/docs` 或 `/docs/**`，不能匹配 `/docs2`。
- 禁用或初始化失败的 storage 不进入运行时表。

`StorageResolver`：

```java
ResolvedStoragePath resolve(String rawPath);
```

`ResolvedStoragePath`：

```java
public record ResolvedStoragePath(
    MountedStorageRuntime runtime,
    String requestPath,
    String actualPath
) {}
```

`actualPath` 计算：

- 请求路径等于 `mountPath` 时，actualPath 为 `/`。
- 请求路径在挂载路径下时，去掉 mountPath 前缀后规范化。

## 8. 服务流程

### 8.1 启动加载存储

新增 `StorageRuntimeInitializer` 或由 `StorageApplicationService` 在 `ApplicationRunner` 中加载：

```text
应用启动
  -> 查询 disabled=false 的 storages
  -> 对每条 storage 创建驱动并 init
  -> 成功：status=work，写入 MountedStorageRegistry
  -> 失败：status=init_error，记录错误日志，不阻塞其他 storage
```

说明：

- M3 可让单条 storage 初始化失败不阻塞服务启动。
- 管理员可修正配置后调用 update/enable 重新挂载。

### 8.2 创建存储

```text
AdminStorageController.create
  -> StorageApplicationService.create
  -> normalize mountPath
  -> 校验 driver 存在
  -> 校验 mountPath 唯一
  -> 写入 storages
  -> disabled=false 时 mount
  -> mount 成功更新 status=work
  -> mount 失败更新 status=init_error 并抛 BAD_REQUEST
```

### 8.3 更新存储

```text
AdminStorageController.update
  -> 查询 storage
  -> 校验 mountPath 唯一，不包括自身
  -> 先 unmount 原运行时
  -> 更新 DB
  -> disabled=false 时重新 mount
```

### 8.4 启用 / 禁用 / 删除

- enable：`disabled=false`，尝试 mount，成功后 `status=work`。
- disable：`disabled=true`，`status=disabled`，unmount。
- delete：先 unmount，再删除 DB 记录。

## 9. `/api/fs/list` 行为

根目录 `/`：

- 如果没有挂载点，返回空列表。
- 如果有挂载点，返回一级虚拟目录项。
- 例：挂载 `/local` 和 `/docs/sub`，根目录显示 `local`、`docs`。M3 可先显示所有一级入口，后续再做虚拟目录树完整聚合。

挂载路径下：

- `StorageResolver` 命中运行时。
- `actualPath` 传给驱动。
- 驱动返回对象后，响应中的 `path` 要映射回用户请求路径：
  - mountPath `/local`
  - actualPath `/a.txt`
  - response path `/local/a.txt`

分页：

- `perPage=-1` 返回全部。
- 否则按 `page/perPage` 截取。
- `hasMore = page * perPage < total`。

## 10. 异常处理

| 场景 | 错误码 | HTTP |
| --- | --- | --- |
| 驱动不存在 | `BAD_REQUEST` | 400 |
| 挂载路径冲突 | `BAD_REQUEST` | 400 |
| storage 不存在 | `OBJECT_NOT_FOUND` | 404 |
| rootPath 为空、非绝对路径、不存在、非目录 | `BAD_REQUEST` | 400 |
| LocalDriver 路径穿越 | `PERMISSION_DENIED` | 403 |
| 文件或目录不存在 | `OBJECT_NOT_FOUND` | 404 |
| 未匹配到存储挂载 | `STORAGE_NOT_FOUND` | 404 |
| 禁用 storage 被访问 | `STORAGE_NOT_FOUND` | 404 |

所有业务异常统一 `throw new BusinessException(ErrorCode.XXX, message)`。

## 11. 测试策略

### 11.1 单元测试

| 测试类 | 覆盖点 |
| --- | --- |
| `LocalDriverTest` | list/get/link、路径穿越防护、rootPath 校验 |
| `MountedStorageRegistryTest` | 最长前缀匹配、禁用 storage 不挂载、unmount |
| `StorageResolverTest` | requestPath 到 actualPath 的转换 |

### 11.2 应用服务测试

| 测试类 | 覆盖点 |
| --- | --- |
| `StorageApplicationServiceTest` | 创建 Local 存储、重复 mountPath、禁用/启用/删除 |
| `DriverApplicationServiceTest` | driver list/names 包含 Local |

### 11.3 Web / FS 集成测试

使用 `@SpringBootTest` + H2 + `@TempDir`：

- admin 调用 `/api/admin/driver/list` 能看到 Local。
- admin 创建 Local 存储后，`/api/fs/list` 能列出临时目录文件。
- 禁用 storage 后，访问挂载路径返回 404。
- 普通用户访问 `/api/admin/storage/list` 返回 403。

验收命令：

```bash
mvn compile -q
mvn test
```

## 12. 实现顺序

1. 新增 `DriverInfo`、`DriverItem` 并扩展 `StorageDriverFactory` / `StorageDriverRegistry`。
2. 新增 LocalDriver addition、factory、driver。
3. 新增 storage 请求/响应 DTO。
4. 新增 `MountedStorageRuntime`、`MountedStorageRegistry`、`StorageResolver`。
5. 实现 `StorageApplicationService` 和启动加载。
6. 新增 AdminDriver / AdminStorage Controller。
7. 改造 `FsApplicationService.list` 接入挂载和 LocalDriver。
8. 补充测试并运行 `mvn compile -q`、`mvn test`。

## 13. 待确认项

- 已确认 M3 另起 `feat(storage)/m3-local-driver` 分支开发，基于 M2 commit 继续推进。
- 已确认 LocalDriver 的 `rootPath` 必须限制在应用级白名单目录下。
- 已确认 M3 的 `/api/fs/list /` 可以先返回一级挂载入口，完整虚拟目录树聚合后续再增强。
