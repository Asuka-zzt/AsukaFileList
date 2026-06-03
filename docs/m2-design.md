# M2 设计文档：认证、用户与角色权限

## 1. 范围说明

`docs/overview-design.md` 的阶段表把 M2 描述为文件写操作，`docs/development-plan.md` 把 M2 描述为认证、用户与角色权限。结合当前分支仍处于 `m0-m1-init` 后续阶段，本设计以 `docs/development-plan.md` 的 M2 为准。

本阶段目标：

- 实现用户登录、JWT 签发与校验。
- 实现当前用户查询和基础信息更新。
- 实现管理员用户与角色的最小管理接口。
- 建立角色路径权限合并规则，为后续文件系统接口做访问控制。
- 首次启动创建 admin / guest 基础角色；admin 用户必建，guest 用户仅在显式启用时创建。

本阶段不实现：

- Redis token 黑名单。`logout` 先返回成功，后续接 Redis 后补充服务端失效。
- 完整文件系统权限拦截。M2 提供权限服务，M3/M4 接入存储和文件读写时调用。
- Web 前端页面。

## 2. 接口签名

### 2.1 认证接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 用户登录，返回 Bearer token |
| `GET` | `/api/auth/logout` | 登出，M2 为客户端删除 token 的语义 |

`POST /api/auth/login`

请求：

```json
{
  "username": "admin",
  "password": "password"
}
```

响应：

```json
{
  "tokenType": "Bearer",
  "accessToken": "jwt",
  "expiresAt": "2026-06-01T00:00:00Z",
  "user": {
    "id": 1,
    "username": "admin",
    "basePath": "/",
    "permission": 131071,
    "admin": true
  }
}
```

### 2.2 当前用户接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/me` | 当前用户 |
| `POST` | `/api/me/update` | 更新当前用户基础信息 |

`POST /api/me/update`

请求：

```json
{
  "oldPassword": "old-password",
  "newPassword": "new-password"
}
```

说明：

- M2 只允许用户修改自己的密码。
- 密码更新后刷新 `users.password_ts`，使旧 token 失效。
- 用户名、`basePath`、权限和角色由管理员接口管理。

### 2.3 管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/user/list` | 用户列表 |
| `POST` | `/api/admin/user/create` | 创建用户 |
| `GET` | `/api/admin/role/list` | 角色列表 |
| `POST` | `/api/admin/role/create` | 创建角色 |

`POST /api/admin/user/create`

请求：

```json
{
  "username": "alice",
  "password": "password",
  "basePath": "/alice",
  "permission": 15,
  "disabled": false,
  "roleIds": [2]
}
```

`POST /api/admin/role/create`

请求：

```json
{
  "name": "reader",
  "description": "只读角色",
  "defaultRole": false,
  "permissionScopes": [
    {
      "path": "/docs",
      "permission": 1
    }
  ]
}
```

## 3. 数据库变更

M1 的 `V1__init_schema.sql` 已包含 M2 需要的表：

- `users`
- `roles`
- `user_roles`

本阶段不新增表字段，不新增 Flyway 脚本。

需要补齐的持久化代码：

| 文件 | 说明 |
| --- | --- |
| `UserRoleEntity.java` | `user_roles` 关联表实体 |
| `UserRoleMapper.java` | 用户角色关联 Mapper |

角色权限继续使用 `roles.permission_scopes` 的 JSON 文本字段，由应用层用 Jackson 序列化和反序列化为 `PermissionScope` 列表。

## 4. 代码结构

新增包和核心类：

```text
domain/user/
  User.java
  Role.java
  PermissionScope.java
  PermissionBits.java
  UserRole.java

application/auth/
  AuthApplicationService.java
  PasswordService.java
  TokenService.java
  CurrentUserService.java

application/user/
  UserApplicationService.java
  RoleApplicationService.java
  PermissionApplicationService.java

infrastructure/security/
  JwtTokenProvider.java
  AuthenticationInterceptor.java
  CurrentUser.java
  CurrentUserArgumentResolver.java
  SecurityWebMvcConfig.java

infrastructure/bootstrap/
  DefaultAccountInitializer.java

api/controller/
  AuthController.java
  MeController.java
  AdminUserController.java
  AdminRoleController.java

api/request/
  LoginRequest.java
  UpdateMeRequest.java
  AdminCreateUserRequest.java
  AdminCreateRoleRequest.java
  PermissionScopeRequest.java

api/response/
  LoginResponse.java
  CurrentUserResponse.java
  UserResponse.java
  RoleResponse.java
```

## 5. 依赖方案

密码哈希使用 Spring Security Crypto 的 BCrypt：

```xml
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-crypto</artifactId>
</dependency>
```

JWT 使用 Spring Security OAuth2 JOSE / Nimbus：

```xml
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>
```

两个依赖版本由 Spring Boot parent 管理，不在 `pom.xml` 单独覆盖版本。

不引入 `spring-boot-starter-security`，避免默认安全过滤链影响现有 Controller。M2 使用自定义 `HandlerInterceptor` 明确控制受保护路径。

## 6. 核心流程

### 6.1 登录

```text
Client
  -> AuthController.login
  -> AuthApplicationService.login
  -> UserMapper 按 username 查询用户
  -> 校验 disabled=false
  -> PasswordService.matches(raw, hash)
  -> 查询用户角色和 admin 身份
  -> TokenService.issue(user)
  -> 返回 LoginResponse
```

JWT claims：

| claim | 说明 |
| --- | --- |
| `sub` | `users.id` |
| `username` | 用户名，便于日志和展示 |
| `pwdTs` | `users.password_ts`，用于密码变更后失效旧 token |
| `iat` | 签发时间 |
| `exp` | 过期时间 |

签名算法：HS256。密钥来自 `asuka.jwt.secret`，生产环境通过 `JWT_SECRET` 注入。

### 6.2 请求鉴权

```text
HTTP request
  -> AuthenticationInterceptor
  -> 跳过 /api/auth/login、/actuator/**、/api/health
  -> 读取 Authorization: Bearer <token>
  -> TokenService.parse
  -> UserMapper.selectById
  -> 校验用户存在、未禁用、password_ts 与 token claim 一致
  -> request attribute 写入 CurrentUser
  -> Controller 通过 @CurrentUser 参数解析获得当前用户
```

管理员接口额外校验：

- `/api/admin/**` 必须具备 admin 角色。
- admin 角色判定使用 `roles.name = 'admin'`，避免在 M2 修改用户表结构。

### 6.3 修改密码

```text
Client
  -> MeController.update
  -> CurrentUserService 获取当前用户
  -> PasswordService.matches(oldPassword, user.passwordHash)
  -> PasswordService.hash(newPassword)
  -> 更新 password_hash/password_salt/password_ts
  -> 返回成功
```

说明：

- BCrypt hash 自带 salt；为兼容已有 `password_salt` 字段，M2 将该字段写入空字符串或保留随机审计 salt，但验证以 BCrypt hash 为准。
- `password_ts` 使用当前毫秒时间戳。

### 6.4 创建用户

```text
AdminUserController.create
  -> 校验 CurrentUser admin
  -> normalize basePath
  -> 校验 username 唯一
  -> hash password
  -> 插入 users
  -> 校验 roleIds 存在
  -> 批量插入 user_roles
```

### 6.5 创建角色

```text
AdminRoleController.create
  -> 校验 CurrentUser admin
  -> 校验 role name 唯一
  -> normalize permissionScopes.path
  -> 校验 permission 非负且不超过 PermissionBits.ALL
  -> permissionScopes 序列化为 JSON
  -> 插入 roles
```

### 6.6 角色权限合并

`PermissionApplicationService` 提供以下能力：

```java
int resolvePermission(CurrentUser user, String requestPath);
boolean hasPermission(CurrentUser user, String requestPath, int requiredBit);
String resolveUserVisiblePath(CurrentUser user, String requestPath);
```

规则：

1. 所有路径先经过 `PathUtils.fixAndCleanPath`。
2. 真实可见路径用 `PathUtils.joinBasePath(user.basePath(), requestPath)` 计算。
3. 基础权限从 `users.permission` 开始。
4. 如果请求路径等于 `/` 或用户 `basePath`，合并用户所有角色的 scope 权限。
5. 如果请求路径落在某个 `PermissionScope.path` 下，合并该 scope 的权限。
6. 如果用户开启路径限制位，目标路径必须命中至少一个角色 scope，否则返回无权限。

`PermissionBits` 与 `detailed-design.md §3.1` 对齐：

| bit | 权限 |
| --- | --- |
| 0 | 查看隐藏文件 |
| 1 | 免目录密码访问 |
| 2 | 添加离线下载 |
| 3 | 新建目录/上传 |
| 4 | 重命名 |
| 5 | 移动 |
| 6 | 复制 |
| 7 | 删除 |
| 8 | WebDAV 读 |
| 9 | WebDAV 写 |
| 10 | FTP/SFTP 读 |
| 11 | FTP/SFTP 写 |
| 12 | 读压缩包 |
| 13 | 解压 |
| 14 | 路径限制 |
| 15 | MCP 读 |
| 16 | MCP 写 |

## 7. 初始化账号

新增 `DefaultAccountInitializer`：

- 启动时查询是否存在 `roles.name = 'admin'`，不存在则创建。
- 启动时查询是否存在 `roles.name = 'guest'`，不存在则创建。
- 启动时查询是否存在 admin 用户，按配置创建。
- `guest-enabled=true` 时查询是否存在 guest 用户，按配置创建。

配置项：

```yaml
asuka:
  bootstrap:
    admin-username: ${ASUKA_ADMIN_USERNAME:admin}
    admin-password: ${ASUKA_ADMIN_PASSWORD:}
    guest-username: ${ASUKA_GUEST_USERNAME:guest}
    guest-password: ${ASUKA_GUEST_PASSWORD:}
    guest-enabled: ${ASUKA_GUEST_ENABLED:false}
```

约束：

- `admin-password` 为空时直接启动失败，避免部署出无管理员或弱初始化状态。
- M2 不把明文默认密码写入仓库配置。

## 8. 异常处理

| 场景 | 错误码 | HTTP |
| --- | --- | --- |
| 登录用户名或密码错误 | `UNAUTHORIZED` | 401 |
| token 缺失、非法、过期 | `UNAUTHORIZED` | 401 |
| 用户被禁用 | `UNAUTHORIZED` | 401 |
| 非管理员访问 `/api/admin/**` | `PERMISSION_DENIED` | 403 |
| 旧密码错误 | `BAD_REQUEST` | 400 |
| 用户名或角色名重复 | `BAD_REQUEST` | 400 |
| 用户或角色不存在 | `OBJECT_NOT_FOUND` | 404 |
| 权限 JSON 无法解析 | `BAD_REQUEST` | 400 |

所有业务异常统一 `throw new BusinessException(ErrorCode.XXX, message)`，不抛裸 `RuntimeException`。

## 9. 测试策略

### 9.1 单元测试

| 测试类 | 覆盖点 |
| --- | --- |
| `PasswordServiceTest` | BCrypt hash 不等于明文、同一密码可验证、错误密码失败 |
| `JwtTokenProviderTest` | 签发、解析、过期、密码时间戳 claim |
| `PermissionApplicationServiceTest` | basePath 拼接、scope 命中、路径限制、权限位合并 |

### 9.2 Mapper / 应用服务测试

| 测试类 | 覆盖点 |
| --- | --- |
| `UserRoleMapperTest` | 插入、按 userId 查询、删除用户角色 |
| `AuthApplicationServiceTest` | 登录成功、密码错误、禁用用户拒绝 |
| `UserApplicationServiceTest` | 创建用户、重复用户名、角色不存在 |
| `RoleApplicationServiceTest` | 创建角色、scope path 规范化、重复角色名 |

### 9.3 Web 层测试

使用 `@SpringBootTest` + H2：

- `POST /api/auth/login` 成功返回 token。
- 无 token 访问 `GET /api/me` 返回 401。
- 带 token 访问 `GET /api/me` 返回当前用户。
- 普通用户访问 `GET /api/admin/user/list` 返回 403。

验收命令：

```bash
mvn compile -q
mvn test
```

## 10. 实现顺序

1. 补充 `pom.xml` 依赖和 `AsukaProperties.Bootstrap` 配置。
2. 新增 user domain 模型和权限位常量。
3. 新增 `UserRoleEntity`、`UserRoleMapper`。
4. 实现 `PasswordService`、`JwtTokenProvider`、`TokenService`。
5. 实现 `AuthApplicationService` 和当前用户解析。
6. 实现 `AuthenticationInterceptor`、`CurrentUserArgumentResolver`。
7. 实现 Auth / Me Controller。
8. 实现用户和角色管理服务与 Controller。
9. 实现 `DefaultAccountInitializer`。
10. 补齐测试并运行 `mvn compile -q`、`mvn test`。

## 11. 待确认项

- 已确认 M2 按 `docs/development-plan.md` 的认证、用户与角色权限推进。
- 已确认 admin 初始密码为空时启动失败。
- 已确认 `logout` 在 M2 接受 stateless 语义，Redis 黑名单后移到接入 Redis 的阶段。
