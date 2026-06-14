package com.asuka.filelist.application.ai;

import com.asuka.filelist.api.request.FsGetRequest;
import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.api.response.KbDirectoryBatchResponse;
import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.infrastructure.persistence.entity.KbDirectoryBatchEntity;
import com.asuka.filelist.infrastructure.persistence.entity.KbDocumentEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.KbDirectoryBatchMapper;
import com.asuka.filelist.infrastructure.persistence.mapper.KbDocumentMapper;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库「整目录入库 + 增量同步」编排服务。
 *
 * <p>接口同步建批次记录后立即返回 batchId，真正的展开/diff/逐文件提交在 {@link #run} 中异步执行，
 * 前端轮询批次进度。逐文件入库委托 {@link KbApplicationService}（跨 bean 调用 → 每文件独立事务），
 * 单个文件失败只计入 failed，不影响其它文件。
 */
@Service
public class KbDirectorySyncService {

    /** 单批最多展开的受支持文件数，超出按非法请求拒绝（当前 Obsidian vault ~1600，留余量）。 */
    private static final int MAX_FILES = 5000;
    /** 递归子目录最大深度。 */
    private static final int MAX_DEPTH = 16;
    /** 列目录分页大小（与 FsListRequest 上限一致）。 */
    private static final int PAGE_SIZE = 500;
    /** 批次计数刷库节流：每处理 N 个文件落一次进度。 */
    private static final int FLUSH_EVERY = 25;

    private final KbApplicationService kbApplicationService;
    private final FsApplicationService fsApplicationService;
    private final KbDirectoryBatchMapper batchMapper;
    private final KbDocumentMapper docMapper;
    private final ObjectProvider<KbDirectorySyncService> self;

    public KbDirectorySyncService(KbApplicationService kbApplicationService,
                                  FsApplicationService fsApplicationService,
                                  KbDirectoryBatchMapper batchMapper,
                                  KbDocumentMapper docMapper,
                                  ObjectProvider<KbDirectorySyncService> self) {
        this.kbApplicationService = kbApplicationService;
        this.fsApplicationService = fsApplicationService;
        this.batchMapper = batchMapper;
        this.docMapper = docMapper;
        this.self = self;
    }

    /**
     * 启动目录同步批次：校验归属 + 目标必须是目录，建批次记录（同步落库），再异步执行。
     *
     * <p>不加 {@code @Transactional}：批次记录需在异步线程读取前已提交，单条 insert 自动提交即可。
     */
    public long start(CurrentUser user, long kbId, com.asuka.filelist.api.request.KbAddDirectoryRequest request) {
        kbApplicationService.verifyKbOwnership(user, kbId);
        String path = PathUtils.fixAndCleanPath(request.path());
        FileObjectResponse dir = fsApplicationService.get(user, new FsGetRequest(path, null));
        if (!dir.isDir()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Path is not a directory");
        }
        KbDirectoryBatchEntity batch = new KbDirectoryBatchEntity();
        batch.setKbId(kbId);
        batch.setUserId(user.id());
        batch.setSourcePath(path);
        batch.setStatus("running");
        batchMapper.insert(batch);

        boolean recursive = request.recursive() == null || request.recursive();
        self.getObject().run(batch.getId(), user, kbId, path, request.docType(), recursive);
        return batch.getId();
    }

    /** 查询批次进度（归属校验）。 */
    public KbDirectoryBatchResponse getBatch(CurrentUser user, long kbId, long batchId) {
        kbApplicationService.verifyKbOwnership(user, kbId);
        KbDirectoryBatchEntity batch = batchMapper.selectById(batchId);
        if (batch == null || !batch.getKbId().equals(kbId)) {
            throw new BusinessException(ErrorCode.KB_NOT_FOUND);
        }
        return KbDirectoryBatchResponse.of(batch);
    }

    /** 后台执行：展开目录 → 载入已有文档指纹 → 逐文件 NEW/MODIFIED/UNCHANGED → 更新批次。 */
    @Async("asukaTaskExecutor")
    public void run(long batchId, CurrentUser user, long kbId, String path, String docType, boolean recursive) {
        KbDirectoryBatchEntity batch = batchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        try {
            Collected collected = collectFiles(user, path, recursive);
            batch.setTotal(collected.files.size());
            batch.setSkipped(collected.skipped);
            Map<String, KbDocumentEntity> existing = loadExistingDocs(kbId, path);

            int processed = 0;
            for (FileObjectResponse file : collected.files) {
                processOne(kbId, user, file, docType, existing, batch);
                if (++processed % FLUSH_EVERY == 0) {
                    batchMapper.updateById(batch);
                }
            }
            batch.setStatus("completed");
        } catch (BusinessException ex) {
            batch.setStatus("failed");
            batch.setErrorMsg(ex.getMessage());
        } catch (RuntimeException ex) {
            batch.setStatus("failed");
            batch.setErrorMsg("directory sync failed");
        }
        batchMapper.updateById(batch);
    }

    /** 单个文件：按指纹决定新增/重建/跳过，失败只计入 failed。 */
    private void processOne(long kbId, CurrentUser user, FileObjectResponse file, String docType,
                            Map<String, KbDocumentEntity> existing, KbDirectoryBatchEntity batch) {
        String filePath = PathUtils.fixAndCleanPath(file.path());
        try {
            KbDocumentEntity prior = existing.get(filePath);
            if (prior == null) {
                kbApplicationService.indexSingleFile(kbId, user.id(), filePath, file, docType);
                batch.setAdded(batch.getAdded() + 1);
            } else if (changed(prior, file)) {
                kbApplicationService.reindexFile(kbId, prior.getId(), file, docType);
                batch.setUpdated(batch.getUpdated() + 1);
            } else {
                batch.setUnchanged(batch.getUnchanged() + 1);
            }
        } catch (RuntimeException ex) {
            batch.setFailed(batch.getFailed() + 1);
        }
    }

    /** size 或 mtime 任一变化即视为改动；历史无指纹则保守判为改动以重建。 */
    private boolean changed(KbDocumentEntity prior, FileObjectResponse file) {
        if (prior.getFileSize() == null || prior.getSourceModified() == null) {
            return true;
        }
        LocalDateTime modified = KbApplicationService.toFingerprintTime(file.modified());
        return prior.getFileSize() != file.size() || !Objects.equals(prior.getSourceModified(), modified);
    }

    /** 递归（或单层）展开目录下的受支持文件，统计被跳过的不支持文件数，受 maxFiles/maxDepth 限制。 */
    private Collected collectFiles(CurrentUser user, String root, boolean recursive) {
        List<FileObjectResponse> files = new ArrayList<>();
        int skipped = 0;
        Deque<DirEntry> stack = new ArrayDeque<>();
        stack.push(new DirEntry(root, 0));
        while (!stack.isEmpty()) {
            DirEntry dir = stack.pop();
            int page = 1;
            while (true) {
                FsListResponse listed = fsApplicationService.list(user,
                        new FsListRequest(dir.path(), null, false, page, PAGE_SIZE));
                for (FileObjectResponse item : listed.content()) {
                    if (item.isDir()) {
                        if (recursive && dir.depth() < MAX_DEPTH) {
                            stack.push(new DirEntry(item.path(), dir.depth() + 1));
                        }
                    } else if (isSupported(item.name())) {
                        files.add(item);
                        if (files.size() > MAX_FILES) {
                            throw new BusinessException(ErrorCode.BAD_REQUEST,
                                    "Directory exceeds maxFiles=" + MAX_FILES + ", narrow the scope");
                        }
                    } else {
                        skipped++;
                    }
                }
                if (!listed.hasMore()) {
                    break;
                }
                page++;
            }
        }
        return new Collected(files, skipped);
    }

    /** 载入该 KB 中位于此目录前缀下的已有文档，按规范化源路径建索引。 */
    private Map<String, KbDocumentEntity> loadExistingDocs(long kbId, String dirPath) {
        String prefix = dirPath.endsWith("/") ? dirPath : dirPath + "/";
        List<KbDocumentEntity> docs = docMapper.selectList(new LambdaQueryWrapper<KbDocumentEntity>()
                .eq(KbDocumentEntity::getKbId, kbId)
                .likeRight(KbDocumentEntity::getSourcePath, prefix));
        Map<String, KbDocumentEntity> map = new HashMap<>();
        for (KbDocumentEntity doc : docs) {
            map.put(PathUtils.fixAndCleanPath(doc.getSourcePath()), doc);
        }
        return map;
    }

    /** 受支持类型：与 parse_service 一致，仅 PDF 与 Markdown。 */
    private boolean isSupported(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    /** 待遍历目录及其深度。 */
    private record DirEntry(String path, int depth) {
    }

    /** 展开结果：受支持文件列表 + 不支持被跳过数。 */
    private record Collected(List<FileObjectResponse> files, int skipped) {
    }
}
