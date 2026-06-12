# 知识库「整目录入库 + 增量同步」设计（方案 ②）

> 状态：已实现，待人工验收
> 关联：`docs/2026-06-10-agentic-graph-rag-kb.md`、`KbApplicationService.addDocument`

## 1. 解决的问题

知识库当前只能逐个文件加入，且显式拒绝目录。面对 Obsidian 这类整目录
（实测 `/mnt/d/iAsuka/obsidian` 下 **1592 个 md + 3 个 pdf ≈ 1595 文件 / 25 MB**），
需要：

1. **整目录批量入库**：一次把目录下受支持文件加入知识库。
2. **增量同步**：目录之前已入库，用户再次入库时，只处理「新增」和「已改动」的文件，
   未变文件零成本跳过；改动文件删旧索引重建。

## 2. 关键决策（已确认）

- 执行方式：**异步批次**（接口返回 batchId，后台 worker 展开+提交，前端轮询进度）。
  原因：1600+ 文件同步展开会撑爆单请求（~20–40s）。
- 变更检测：**size + mtime 指纹**（不读文件内容，rsync 式廉价判定）。
- 已删除文件：**默认保留**，不自动 prune（避免目录没挂全时误删）。

## 3. 总体架构

```
前端「加入/同步整个目录」
   │ POST /api/kb/{kbId}/documents/directory { path, docType, recursive }
   ▼ 立即返回 { batchId }
KbController.addDirectory → KbDirectorySyncService.start(...)   ← 建批次记录 + @Async 触发
   ▼ (后台单线程顺序处理该批次)
KbDirectorySyncService.run(batchId)
   ├─ requireOwnedKb；fs.get(path) 必须是目录
   ├─ 递归 fs.list 收集受支持文件（size+mtime），受 maxFiles/maxDepth 限制
   ├─ 载入该 KB 中 source_path 在此目录前缀下的已有 doc → 指纹表
   └─ for each file（每文件独立事务）:
         NEW       → 建 doc + 提交索引            → added++
         MODIFIED  → 重置 doc + 重新提交索引(同 docId) → updated++
         UNCHANGED → 跳过                          → unchanged++
         不支持类型 → 跳过                          → skipped++
         单文件异常 → 记 failed，不影响其它          → failed++
   └─ 批次 status=completed（或 failed）

前端轮询 GET /api/kb/{kbId}/batches/{batchId} 看 added/updated/unchanged/failed/total/status
之后沿用现有 per-doc 状态轮询看真正索引进度（pending→…→indexed）。
```

## 4. 实现方式

### 4.1 指纹与 diff

`kb_document` 新增两列存指纹：`file_size`、`source_modified`（文件 mtime，来自
`FileObjectResponse.size / modified`，无需读内容）。

diff 按「目录路径前缀」比对该 KB 已有 doc：

| 情况 | 判定 | 动作 |
| --- | --- | --- |
| 路径不在库 | NEW | 建 doc + 提交索引 |
| 路径在库，size 或 mtime 变 | MODIFIED | 删旧索引 + 同 docId 重建 |
| 路径在库，size+mtime 不变 | UNCHANGED | 跳过，不碰 AI |
| 在库但目录里已无 | DELETED | 保留（默认不动） |
| 非 PDF/MD | 不支持 | 跳过并计入 skipped |

### 4.2 改动文件的重建（删旧 + 重建，复用稳定 docId）

- `lightragDocId = kb{kbId}-doc{docId}` 确定且稳定，MODIFIED **复用同一 doc 行**，
  不新建，保证引用一致、不产生孤儿索引。
- **把索引任务做成幂等**：Celery `task_kb_index` 在 insert 前先 `adelete_by_doc_id`
  （不存在则 no-op），再 `ainsert`。于是「改动重建」= 更新指纹 + 状态重置 pending +
  重新提交索引；顺带根治重复提交/重试残留。这是本设计唯一的 Python 改动。
- Java 侧 MODIFIED 流程：更新 `file_size/source_modified`、`status=pending`、清 `error_msg`，
  调 `submitKbIndex`（同 lightragDocId）。

### 4.3 复用单文件逻辑

抽出 `addDocument` 的「建 doc + 提交索引」主体为
`indexSingleFile(...)`（`@Transactional`）。单文件接口、目录批次的 NEW/MODIFIED 都复用它，
每文件一个事务，单个失败不回滚其它（编排层不加事务，经自注入代理逐文件提交）。

### 4.4 安全上限

| 限制 | 默认 | 说明 |
| --- | --- | --- |
| `maxFiles` | 5000 | 单批最多展开文件数（当前 vault ~1595，留余量）；超出批次 failed 并提示缩小范围 |
| `maxDepth` | 16 | 递归最大深度 |

### 4.5 重启与并发

- 批次后台用单线程顺序处理（重负载在下游 Celery，编排只做快速 DB+入队，不需要并行）。
- Java 重启时，残留 `running` 批次按现有 `StaleTaskRecovery` 思路标记 `failed`；因 diff
  幂等，用户重新发起同目录同步即可安全续跑（UNCHANGED 自动跳过）。

## 5. 接口与数据

### 5.1 新增 Flyway 迁移 `V{n}__add_kb_directory_sync.sql`

```sql
ALTER TABLE kb_document
  ADD COLUMN file_size       BIGINT,
  ADD COLUMN source_modified DATETIME(3);

CREATE TABLE kb_directory_batch (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    kb_id       BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    source_path VARCHAR(1000) NOT NULL,
    -- running | completed | failed
    status      VARCHAR(20)  NOT NULL DEFAULT 'running',
    total       INT          NOT NULL DEFAULT 0,
    added       INT          NOT NULL DEFAULT 0,
    updated     INT          NOT NULL DEFAULT 0,
    unchanged   INT          NOT NULL DEFAULT 0,
    skipped     INT          NOT NULL DEFAULT 0,
    failed      INT          NOT NULL DEFAULT 0,
    error_msg   VARCHAR(2000),
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_kbbatch_kb (kb_id),
    CONSTRAINT fk_kbbatch_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 5.2 接口

```
POST /api/kb/{kbId}/documents/directory   登录态 + KB 归属
  body: { path: string, docType?: string, recursive?: boolean=true }
  resp: { batchId: number, status: "running" }
  err : 路径非目录 → BAD_REQUEST（单文件走原 /documents）

GET  /api/kb/{kbId}/batches/{batchId}     登录态 + KB 归属
  resp: { id, status, total, added, updated, unchanged, skipped, failed, sourcePath }
```

数据：`kb_document` 状态机不变；新增 `kb_directory_batch` 仅记录批次进度。

## 6. 取舍、限制与风险

- **只入 PDF + Markdown**（当前 `parse_service` 仅支持这两类）；其余计入 `skipped`，
  不产生失败 doc 噪声。docx/xlsx 待解析端扩充后再放开。
- **mtime 误判**：仅改 mtime 未改内容会触发一次多余重建；可接受。
- **批量成本（重要）**：1600 篇 md 每篇都要 bge-m3 向量化 + DeepSeek 抽实体关系，
  且 bge-m3 串行 + Celery Redis 锁，**实际可能串行跑数小时、DeepSeek token 消耗可观**。
  增量同步正是为缓解此问题：改一篇只重建一篇。首次全量入库仍建议分批、预留时间与额度。
- **DELETED 不清理**：目录删文件不会同步删 KB（默认保留），需要时用户手动删 doc。
- **批次重启**：见 §4.5，幂等可重跑。

## 7. 后续可改进

- 同步删除选项（prune）。
- 内容哈希指纹（解析端回填 hash，避免 mtime 误判）。
- 扩展过滤（仅 `*.pdf`）、批次并发与限流配置。
- 批次进度细化到「正在处理第 N/总」。

## 8. 测试计划

- **Java（service + controller）**
  - 首次目录入库：仅 PDF/MD 进 added，其余 skipped；返回 batchId。
  - 二次同步：未改文件 UNCHANGED；改 mtime/size 的文件 MODIFIED 走删旧+重建（验证调
    deleteKbDocument/submitKbIndex 同 docId）；新增文件 NEW。
  - 单文件提交失败 → failed，其它不受影响（隔离）。
  - 归属校验：跨用户 KB → KB_NOT_FOUND。
  - 路径为文件 → BAD_REQUEST；空目录 → 空批次 completed。
  - `recursive=false` 只取一层；超 maxFiles → 批次 failed。
  - 批次查询：归属校验 + 计数正确。
- **Python**：`task_kb_index` 幂等性（insert 前 delete-by-id）单测。
- **前端**：「加入/同步整个目录」交互，构建 + lint + 手工验收。

## 9. 提交拆分

1. `feat(db): kb_document 指纹列与 kb_directory_batch 迁移`
2. `feat(ai): KB 整目录异步批次入库与增量 diff`（含抽出 indexSingleFile）
3. `fix(ai): KB 索引任务幂等（重建前删旧 doc_id）`
4. `test(ai): KB 目录入库与增量同步单测`
5. `test(ai): KB 索引任务幂等性单测`
6. `feat(web): 知识库加入/同步整个目录与批次进度`
7. `docs(ai): 记录整目录入库与增量同步设计`

## 10. 实现与验证结果

截至 2026-06-12：

- Java：`mvn test` 92 passed（含新增 `KbDirectorySyncTest` 2 项：首次递归入库+类型
  过滤、二次未变同步、改动重建；路径非目录 400、跨用户 404）。
- Python：`pytest` 26 passed（含索引任务幂等性：delete 在 insert 前、解析失败不删旧）。
- Web：`npm run lint` + `npm run build` 通过。
- 待人工验收：真实环境对 Obsidian 目录（~1595 个 md/pdf）首次入库与改动后增量同步。
