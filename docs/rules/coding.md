# 编码规范详细说明

## 行数上限

| 单位 | 推荐上限 | 硬上限 | 超出时的处理 |
|------|----------|--------|--------------|
| 单个方法 / 函数 | 50 行 | 80 行 | 拆分为私有方法或独立类 |
| 单个文件 | 300 行 | 500 行 | 按职责拆分（如 ServiceImpl + Helper） |
| SQL 单条语句 | 20 行 | — | 移入 XML Mapper 或命名函数 |

> 计数规则：空行和注释行不计入，import 块不计入。  
> 超过推荐上限给出警告；超过硬上限必须拆分，不得提交。

## 最小改动原则

- 只改与需求直接相关的代码，不顺手重构无关部分。
- 若发现需要重构的代码，另开 `refactor/<desc>` 分支单独处理。
- 新增依赖前先确认 parent POM 是否已管理该版本，避免版本冲突。

## 编码规范

```
✅ 方法职责单一：一个方法只做一件事，委托给私有方法而非堆砌逻辑
✅ 使用已有工具类，不重复造轮子
     PathUtils.sanitizePath / validateMagicBytes
     JwtUtil.createToken / parseToken
✅ 类型注解必须完整（Java 泛型、Python type hint）
✅ 外部 I/O（HTTP / DB）必须有超时和错误处理
✅ 异常使用项目统一机制，不抛裸异常
     Java  → throw new BusinessException(ErrorCode.XXX)
     Python → raise HTTPException(status_code=..., detail=...)
✅ 数据库操作必须在 @Transactional / session 块内，不裸调

❌ 禁止
     裸 RuntimeException / Exception 直接抛出
     str == null || str.equals("") （用 StringUtils.isBlank）
     路由 / Controller 层写业务逻辑
     Domain / Entity 类注入 Spring Bean
```

## 注解规范

1. **文件分区**：单文件有多个业务模块时，必须用标准横幅注释做区域分割，禁止模块混杂无边界。
2. **强制注释范围**：所有类、自定义函数、复杂业务代码块、特殊常量、分支逻辑均必须加注释；基础变量定义、一目了然的简单运算无需冗余注释。
3. **格式统一**：全文横幅注释格式固定，不自定义分隔符、不修改横幅长度。

禁止事项：
- 禁止省略模块横幅，同一文件多逻辑不做分区隔离
- 禁止无注释封装类、工具函数、接口请求函数
- 禁止模糊注释：不允许仅写【工具方法】【业务处理】这类无意义描述
- 禁止注释和代码逻辑不一致：代码迭代后必须同步更新注释内容

## 新增功能 Checklist

- [ ] `docs/` 下有对应设计文档
- [ ] 在 `feat/<desc>` 分支开发，不在 main 直接提交
- [ ] 新增数据库表/字段有对应 Flyway 迁移脚本（`V{n}__desc.sql`）
- [ ] 涉及用户数据的接口做了所有权校验（`userId` 匹配）
- [ ] 异常路径有对应 `ErrorCode` 错误码，不抛裸异常
- [ ] 单个文件 ≤ 500 行（推荐 ≤ 300），单个方法 ≤ 80 行（推荐 ≤ 50）
- [ ] `mvn compile -q` 无报错
