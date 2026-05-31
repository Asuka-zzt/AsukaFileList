# M0 & M1 设计文档

## 需求背景

从当前 Spring Boot 脚手架演进：
- M0：补齐本地开发基础设施（docker-compose、环境变量、README）。
- M1：建立 MySQL 业务库迁移体系（Flyway + MyBatis Plus），为后续 Auth、Storage、Fs 各模块打底。

## 方案设计

### M0：工程治理与本地开发基线

不涉及代码变更，仅补充工程配置文件：

| 文件 | 变更说明 |
|------|---------|
| `docker-compose.yml` | 新增；MySQL 8 / pgvector-pg16 / Redis 7；`profiles: app` 可选启动 Java/Python 容器 |
| `.env.example` | 补全 `DB_HOST`、`DB_PORT`、`REDIS_HOST`、`REDIS_PORT` |
| `README.md` | 增加四步本地启动顺序、API 验证命令、阶段进度表 |

验收：`docker compose up -d mysql postgres redis` 三个容器健康；`mvn test` 绿。

---

### M1：数据库与迁移体系

#### ORM 选型

使用 **MyBatis Plus 3.5.x**，理由：
- 与项目现有风格一致（详细设计 §1 注明"JPA 或 MyBatis Plus"，用户指定 MP）。
- Mapper 接口对 SQL 可见性更好，便于后续复杂查询调优。
- 通用 CRUD 接口（`BaseMapper<T>`）减少样板代码。

#### 接口签名

M1 不新增对外 HTTP 接口，仅建立内部数据访问层。

#### 依赖变更（pom.xml）

```xml
<!-- MyBatis Plus -->
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
  <version>3.5.7</version>
</dependency>
<!-- MySQL 驱动 -->
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <scope>runtime</scope>
</dependency>
<!-- Flyway -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>
<!-- 测试用内存库 -->
<dependency>
  <groupId>com.h2database</groupId>
  <artifactId>h2</artifactId>
  <scope>test</scope>
</dependency>
```

**移除**：`spring-boot-starter-data-jpa`（不使用 JPA）。

#### 数据库变更

新建迁移脚本 `src/main/resources/db/migration/V1__init_schema.sql`，创建 8 张核心表（详见 `detailed-design.md §8`）：

| 表名 | 说明 |
|------|------|
| `users` | 用户，含 `username(UK)`、`password_hash/salt`、`base_path`、`permission` 位 |
| `roles` | 角色，含 `permission_scopes` JSON 字段 |
| `user_roles` | 用户-角色关联（复合主键，双向级联删除） |
| `storages` | 存储挂载，含 `mount_path(UK)`、`driver`、`addition` JSON、`status` |
| `meta_rules` | 目录 meta，含 `path(UK)`、`password`、`hide` 正则、`write_enabled` |
| `shares` | 分享，含 `share_id(UK)`、`creator_id(FK→users)`、`expires_at`、访问次数 |
| `tasks` | 任务，含 `type`、`status`、`progress`、`payload/result` JSON |
| `file_index_nodes` | 文件名索引，含 `parent/name/storage_id` 三个索引 |

#### 核心流程（Entity + Mapper 层）

```
Flyway 启动 → 执行 V1__init_schema.sql → 建表完成
Service 调用 → XxxMapper.selectXxx / insert / updateById / deleteById
```

MP 实体用 `@TableName` / `@TableId` / `@TableField` 注解，不引入 JPA 注解。

#### 新增文件清单

```
src/main/resources/db/migration/V1__init_schema.sql

infrastructure/persistence/entity/
  UserEntity.java
  RoleEntity.java
  StorageEntity.java
  MetaRuleEntity.java
  ShareEntity.java
  TaskEntity.java
  FileIndexNodeEntity.java

infrastructure/persistence/mapper/
  UserMapper.java
  RoleMapper.java
  StorageMapper.java
  MetaRuleMapper.java
  ShareMapper.java
  TaskMapper.java
  FileIndexNodeMapper.java

src/main/resources/application.yml   （新增 datasource / mybatis-plus / flyway 配置段）
src/test/resources/application.yml   （H2 内存库，Flyway 禁用，测试不依赖 MySQL）
```

#### application.yml 变更

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:cloud_disk}?...
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:}
  flyway:
    enabled: true
    locations: classpath:db/migration

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml   # 暂无 XML，预留目录
  global-config:
    db-config:
      id-type: auto                          # 对应 AUTO_INCREMENT
      logic-delete-field: deleted            # 暂不启用，预留
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

#### 异常处理

| 场景 | 处理方式 |
|------|---------|
| DB 连接失败 | Spring Boot 启动失败，Flyway 报错可读 |
| 唯一键冲突 | MP 抛 `DuplicateKeyException`，GlobalExceptionHandler 返回 `BAD_REQUEST` |
| 实体不存在 | Service 层判空，抛 `BusinessException(ErrorCode.OBJECT_NOT_FOUND)` |

#### 测试策略

- **Mapper 测试**：`@MybatisPlusTest`（MP 官方 slice）+ H2；Flyway 禁用，由 `@Sql` 或 `ddl-auto=create-drop` 建表。
- 验证：StorageMapper CRUD + 唯一约束；UserMapper 按 username 查询。
- 现有 `PathUtilsTests` 和 `AsukaFileListApplicationTests` 继续通过。

#### 验收标准

- `mvn compile -q` 无报错。
- `mvn test` 全绿。
- 本地启动后 Flyway 日志显示 `Successfully applied 1 migration`，MySQL 中能看到 8 张表。

## 实现步骤（有序）

1. 清理已有 JPA 实现代码（entity/repository，移除 JPA 依赖）。
2. `pom.xml`：替换为 MP 依赖。
3. 新建 Flyway SQL 迁移文件。
4. 新建 MP Entity 类（使用 `@TableName` 等注解）。
5. 新建 Mapper 接口（继承 `BaseMapper<T>`）。
6. 更新 `application.yml` / `src/test/resources/application.yml`。
7. 编写 Mapper 单元测试。
8. `mvn compile -q && mvn test`。
