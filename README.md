# AsukaFileList

AsukaFileList 目标是逐步搭建一个 Java 版本的 AList，并通过 Python RAG 服务提供语义搜索与知识库问答能力。

## 项目结构

```text
.
├── ai-service/                 # Python FastAPI RAG 服务
├── docs/                       # 概要设计与详细设计
├── ref/                        # 参考项目源码
├── src/main/java/              # Java 主服务
├── src/main/resources/         # Java 主服务配置
├── src/test/java/              # Java 测试
└── pom.xml                     # Maven 工程
```

## Java 主服务

当前脚手架基于 Spring Boot 3.3.x 和 Java 17。后续如切换到 JDK 21，可在 `pom.xml` 中调整 `java.version`。

### 运行测试

```bash
mvn test
```

### 启动服务

```bash
mvn spring-boot:run
```

启动后可访问：

- `GET http://localhost:8080/api/health`
- `GET http://localhost:8080/actuator/health`
- `POST http://localhost:8080/api/fs/list`

`/api/fs/list` 目前返回虚拟根目录，用于验证 API、统一响应和模块分层，后续会接入存储挂载与驱动体系。

## 文档

- `docs/overview-design.md`：概要设计
- `docs/detailed-design.md`：详细设计
