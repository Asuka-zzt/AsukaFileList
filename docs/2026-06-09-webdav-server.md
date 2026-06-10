# WebDAV 服务端：多云盘统一挂载设计

> 状态：已批准，开发中（范围：完整读写 + Digest + 专用 WebDAV 密码）。
> 分支：`feat(webdav)/webdav-server`（已 rebase 到合并 M8 后的 main，含 S3/百度读写以便端到端联调）。

## 1. 解决的问题

目前文件只能经 Web UI / REST 访问。目标是把**所有已挂载存储（Local / S3 / 百度…）以单一 WebDAV 命名空间暴露**，让标准 WebDAV 客户端——Windows「映射网络驱动器」、macOS Finder、rclone 等——把整个网盘挂成一个本地盘符使用。**本地/局域网即可，不需要公网。**

本轮范围：**完整读写**（浏览 / 打开 / 下载 / 上传 / 新建目录 / 删除 / 移动 / 复制），含 Windows 写入所需的 **LOCK 假锁**。

不在范围：WebDAV ACL、版本控制（DeltaV）、配额报告、PROPPATCH 持久化自定义属性、跨存储原子 MOVE/COPY（首版降级为不支持或 copy+del）。

## 2. 关键约束与取舍

- **复用统一 VFS，零业务重复**：WebDAV 只是协议适配层，所有操作落到既有 `FsApplicationService`（list/get/link/mkdir/put/move/copy/remove），沿用其 basePath、权限校验、路径夹紧。
- **独立 Servlet，不走 DispatcherServlet**：`PROPFIND/MKCOL/MOVE/COPY/LOCK` 等非标准 HTTP 方法在 Spring MVC `@RequestMapping` 里支持很差。改用注册在 `/dav/*` 的 `HttpServlet`（`ServletRegistrationBean`），自行按 `request.getMethod()` 分发，完全掌控方法、XML、状态码。符合本项目「手写、最小依赖」风格，**不引入 Milton / Jackrabbit 等 WebDAV 库**（避免大依赖与审批）。
- **认证：Digest + 专用 WebDAV 密码（重要决策）**：
  - 选 Digest 是为了让 Windows 在**明文 HTTP** 上直接挂载（免改注册表、免 TLS）。
  - **但本系统用户密码只存 BCrypt 哈希**，而 Digest 校验需要 `HA1 = MD5(username:realm:password)`，**无法从 BCrypt 反推**。
  - 因此 Digest 必须配一个**独立的 WebDAV 密码**：用户在「个人设置」里设置一次，服务端只存其 `HA1`（不存明文）。挂载时用「用户名 + WebDAV 密码」。
  - 取舍：若不愿加独立密码，可改 **Basic + 复用登录密码**（服务端能 BCrypt 校验客户端明文），但需 TLS 或每台 Windows 设 `BasicAuthLevel=2`——与「免注册表免 TLS」相悖。故本设计采用 Digest + 专用密码。
- **云盘下载必须服务端代理**：Windows Mini-Redirector 不跟随 302。WebDAV GET 一律代理字节流（本地 `file` 直读；S3/百度走直链/带头代理），**不下发预签名 302**。复用并提取 `DownloadController` 的 Range / 分段输出逻辑。
- **路径与命名空间**：WebDAV 路径 `/dav/<挂载点>/<子路径>` 直接对应 `FsApplicationService` 的用户可见路径 `/<挂载点>/<子路径>`；`/dav/` 根 = 虚拟根，列出当前用户可见的挂载点（FsApplicationService 在 `/` 已如此呈现）。

## 3. 总体架构

```
Windows / macOS / rclone ──HTTP(Digest)──▶  /dav/*  (WebDavServlet)
                                                │  Digest 鉴权 → CurrentUser
                                                ▼
                                         WebDavService（协议 ↔ VFS 适配）
                                                │
                                                ▼
                                       FsApplicationService（既有统一 VFS）
                                          ├─ LocalDriver       (读写)
                                          ├─ S3Driver          (读写)
                                          └─ BaiduDriver       (写→不支持)
```

新增/变更模块：

```
infrastructure/webdav/
  WebDavServlet.java              (新) /dav/* 方法分发 + 鉴权入口 + 异常→状态码
  WebDavDigestAuthenticator.java  (新) nonce 生成/校验、HA1 比对、401 challenge
  WebDavXml.java                  (新) PROPFIND 207 Multi-Status 构建、Destination 解析
  WebDavLockManager.java          (新) 内存假锁：LOCK 发 token / UNLOCK 释放 / lockdiscovery
  WebDavStreaming.java            (新) GET 的 Range/本地直读/远程代理输出（自 DownloadController 提取共享）
application/webdav/
  WebDavService.java              (新) 把 WebDAV 语义映射到 FsApplicationService
config/
  WebDavServletConfig.java        (新) ServletRegistrationBean 注册 /dav/*
infrastructure/security/
  AuthenticationInterceptor.java  (改) 已是 MVC 拦截器；/dav 由独立 Servlet 处理，天然不经它，无需改（确认即可）
api/controller/MeController.java  (改) 设置/清除 WebDAV 密码端点（只存 HA1）
domain/user + 迁移
  users.webdav_ha1 列             (新) Flyway V{n}__add_webdav_credential.sql
web/src/pages（个人设置）          (改) 设置 WebDAV 密码的小表单 + 挂载地址提示
```

## 4. 实现批次（实现时微调：鉴权基础先行，避免只读骨架用一次性临时凭据）

- **批次 1 `feat(auth)`：WebDAV 凭据基础** — Flyway `users.webdav_ha1` 迁移 + test-schema 同步；`Me` 设置/清除 WebDAV 密码端点（只存 `HA1=MD5(username:realm:password)`，不存明文）；HA1 单测。
- **批次 2 `feat(webdav)`：只读 DAV** — Servlet + `ServletRegistrationBean` 注册 `/dav/*`；Digest 鉴权（nonce 防重放 + 比对真实 HA1 → `CurrentUser`）；`OPTIONS`（`DAV: 1,2`）、`PROPFIND`（Depth 0/1，207）、`GET`/`HEAD`（本地 Range + 远程代理）。验收：rclone / Windows 只读挂载，可浏览所有存储并拷出文件。
- **批次 3 `feat(webdav)`：写能力** — `PUT`/`MKCOL`/`DELETE`/`MOVE`/`COPY` + `LOCK`/`UNLOCK` 假锁；`Destination`/`Overwrite`/`If` 头处理。验收：Windows 资源管理器增删改移。
- **批次 4 `docs`/`test`** — 前端「个人设置」设 WebDAV 密码小表单 + 挂载地址提示；回填本文 §8、更新 README/`development-plan`、补 Windows 挂载 checklist。

### 实现决策（开发期补充）
- **realm 硬编码为常量 `AsukaFileList`**：HA1 依赖 realm，若做成可配，改 realm 会静默作废所有已存 WebDAV 密码——故固定为常量，不进 `AsukaProperties`（也免去改其测试构造）。
- **HA1 不污染 `User` 领域记录**：`User` record 被大量 `new User(...)` 引用，故 `webdav_ha1` 仅落在 `UserEntity` + `UserApplicationService` 的专用读写方法，鉴权时按需取，缩小影响面。
- **nonce HMAC 复用 `jwt.secret`**：与 `ShareTokenService`/`DownloadSignService` 一致，不新增密钥配置。

## 5. 关键流程

### Digest 鉴权（qop=auth, RFC 2617）
- 无凭据/失败 → `401 WWW-Authenticate: Digest realm="AsukaFileList", qop="auth", nonce=<base64(ts:HMAC(ts))>, opaque=...`；nonce 限时（如 5 min），防重放。
- 客户端二次请求带 `Authorization: Digest ...` → 服务端计算 `response = MD5(HA1:nonce:nc:cnonce:qop:HA2)`，`HA2 = MD5(method:uri)`，`HA1` 取自 `users.webdav_ha1`；通过则构建 `CurrentUser` 注入请求。
- `realm` 固定；`HA1 = MD5(username:realm:webdavPassword)` 在「设置 WebDAV 密码」时算好入库，服务端不存明文。

### PROPFIND
- 解析 `Depth`（0/1；`infinity` 拒绝或限制）。对 `get()`（自身）+ `list()`（Depth 1 子项）产出 `<D:multistatus>`，每项 `<D:response>`：`href`、`<D:propstat>` 含 `displayname`、`resourcetype`（目录=`<D:collection/>`）、`getcontentlength`、`getlastmodified`（RFC1123）、`creationdate`、`getcontenttype`、`supportedlock`。

### GET / HEAD
- `FsApplicationService.link` → 本地 `file` 直读分段；远程**一律代理**（带 `link.headers`，转发 `Range`，回写 200/206 + `Content-Range`/`Length`/`Type`）。复用 `WebDavStreaming`（自 `DownloadController` 提取）。

### PUT / MKCOL / DELETE / MOVE / COPY
- `PUT`：父集合须存在，流式 body → `put`；返回 201/204。
- `MKCOL`：`mkdir`；父不存在 → 409。
- `DELETE`：`remove`；返回 204。
- `MOVE`/`COPY`：解析 `Destination`（同 `/dav` 前缀）→ 同存储内 `move`/`copy`；跨存储首版返回 502/不支持（二期可 copy+del，非原子）。`Overwrite: F` 且目标存在 → 412。

### LOCK / UNLOCK
- `LOCK`：生成 `opaquelocktoken`，内存登记（path → token + 超时），回 200 + `<D:lockdiscovery>` + `Lock-Token` 头。`UNLOCK`：校验并释放，204。首版为「假锁」：满足 Windows 写流程即可，不做强一致排他。

## 6. 异常 → WebDAV 状态码

| 场景 | 状态码 |
| --- | --- |
| 鉴权缺失/失败 | 401（Digest challenge） |
| 无权限 | 403 |
| 对象不存在 | 404 |
| PUT/MKCOL 父集合不存在 | 409 |
| MOVE/COPY 目标已存在且 `Overwrite: F` | 412 |
| 只读驱动写（如百度）/跨存储不支持 | 405 / 502 |
| 远程 I/O（`DRIVER_REMOTE_ERROR`） | 502 |
| 非法 Depth/Destination | 400 |

在 Servlet 边界统一把 `BusinessException(ErrorCode)` 映射为上述状态码。

## 7. 风险与限制

- **Digest + 独立密码**：多一套密码与一次迁移；收益是免注册表、免 TLS。若日后要复用登录密码，只能 Basic + TLS。
- **假锁非真排他**：并发写冲突不防，首版以兼容 Windows 为目标。
- **只读驱动**：百度等写操作返回不支持；Explorer 往百度目录复制会失败（预期行为）。
- **跨存储 MOVE/COPY**：首版不支持（或二期 copy+del，非原子）。
- **大文件 / Windows 限制**：PUT 流式上传；Windows 默认 50MB 上限需调 `FileSizeLimitInBytes`（写入 checklist）。
- **WSL**：Windows→WSL 的 localhost 转发一般可用；否则用 `wsl hostname -I` 的 IP。
- **安全**：`/dav` 仅 Digest，与现有 Bearer 隔离。HA1 等价口令；**公网暴露务必上 HTTPS**，本地/局域网可明文。

## 8. 测试计划（实现后回填）

- **单元**：Digest `response` 计算与 nonce 校验；PROPFIND XML 生成（目录/文件/Depth）；`Destination` 解析；`WebDavLockManager`。
- **集成**（MockMvc / 内嵌容器）：`OPTIONS` 头（`DAV: 1,2`、`MS-Author-Via`）；`PROPFIND` 207；`GET` Range 206；`PUT` → 存储副作用；`MKCOL`/`DELETE`/`MOVE`/`COPY`；`LOCK`/`UNLOCK`；401 challenge → 带凭据 200。
- **手测**：Windows 映射网络驱动器（Digest，明文 HTTP）、rclone、macOS Finder；增删改查 + 大文件。

验收标准：Windows 用「用户名 + WebDAV 密码」经 Digest 在明文 HTTP 下把 `/dav` 映射为网络驱动器；可浏览所有挂载存储、打开/拷出/上传/新建/删除/移动；百度存储只读；`mvn test` 全绿；`npm --prefix web run build` 通过。
