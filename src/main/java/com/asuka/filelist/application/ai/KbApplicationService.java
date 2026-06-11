package com.asuka.filelist.application.ai;

import com.asuka.filelist.api.request.KbAddDocumentRequest;
import com.asuka.filelist.api.request.KbChatRequest;
import com.asuka.filelist.api.request.KbCreateRequest;
import com.asuka.filelist.api.request.FsGetRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.KbDocumentResponse;
import com.asuka.filelist.api.response.KbResponse;
import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.common.config.AsukaProperties;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.infrastructure.persistence.entity.KbDocumentEntity;
import com.asuka.filelist.infrastructure.persistence.entity.KbKnowledgeBaseEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.KbDocumentMapper;
import com.asuka.filelist.infrastructure.persistence.mapper.KbKnowledgeBaseMapper;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import com.asuka.filelist.infrastructure.security.DownloadSignService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 知识库用例服务（P3）：知识库与文档的 CRUD、归属校验、索引/删除/问答代理。
 *
 * <p>所有接口都以 {@link CurrentUser#id()} 校验归属，越权或不存在统一按「未找到」处理，
 * 避免泄露其它用户的资源存在性。向量/图存储不在此处，全部委托 AI 服务（LightRAG）。
 */
@Service
public class KbApplicationService {

    private static final Set<String> DOC_TYPES = Set.of("paper", "book", "note");

    private final KbKnowledgeBaseMapper kbMapper;
    private final KbDocumentMapper docMapper;
    private final FsApplicationService fsApplicationService;
    private final DownloadSignService downloadSignService;
    private final AiServiceClient aiServiceClient;
    private final String internalBaseUrl;

    public KbApplicationService(KbKnowledgeBaseMapper kbMapper,
                                KbDocumentMapper docMapper,
                                FsApplicationService fsApplicationService,
                                DownloadSignService downloadSignService,
                                AiServiceClient aiServiceClient,
                                AsukaProperties properties) {
        this.kbMapper = kbMapper;
        this.docMapper = docMapper;
        this.fsApplicationService = fsApplicationService;
        this.downloadSignService = downloadSignService;
        this.aiServiceClient = aiServiceClient;
        this.internalBaseUrl = trimTrailingSlash(properties.ai().internalBaseUrl());
    }

    // ─── 知识库 CRUD ───────────────────────────────────────────

    /** 创建知识库，回填 workspace = kb_{id}。 */
    @Transactional
    public KbResponse createKb(CurrentUser currentUser, KbCreateRequest request) {
        KbKnowledgeBaseEntity entity = new KbKnowledgeBaseEntity();
        entity.setUserId(currentUser.id());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus("active");
        kbMapper.insert(entity);
        entity.setWorkspace("kb_" + entity.getId());
        kbMapper.updateById(entity);
        return KbResponse.of(entity);
    }

    /** 列出当前用户的知识库（按创建时间倒序）。 */
    public List<KbResponse> listKbs(CurrentUser currentUser) {
        List<KbKnowledgeBaseEntity> list = kbMapper.selectList(
                new LambdaQueryWrapper<KbKnowledgeBaseEntity>()
                        .eq(KbKnowledgeBaseEntity::getUserId, currentUser.id())
                        .orderByDesc(KbKnowledgeBaseEntity::getId));
        return list.stream().map(KbResponse::of).toList();
    }

    /** 删除知识库：先清 LightRAG workspace，再删 MySQL 记录（文档随 FK 级联删除）。 */
    @Transactional
    public void deleteKb(CurrentUser currentUser, long kbId) {
        requireOwnedKb(currentUser, kbId);
        aiServiceClient.deleteKb(kbId);
        kbMapper.deleteById(kbId);
    }

    // ─── 文档管理 ──────────────────────────────────────────────

    /** 把网盘文件加入知识库：校验可读、去重、落记录并提交索引任务。 */
    @Transactional
    public KbDocumentResponse addDocument(CurrentUser currentUser, long kbId, KbAddDocumentRequest request) {
        requireOwnedKb(currentUser, kbId);
        String path = PathUtils.fixAndCleanPath(request.path());
        // 复用 fs.get 校验读权限并判定目录/文件
        FileObjectResponse file = fsApplicationService.get(currentUser, new FsGetRequest(path, null));
        if (file.isDir()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot add a directory to a knowledge base");
        }
        if (findDocument(kbId, path) != null) {
            throw new BusinessException(ErrorCode.KB_DOCUMENT_DUPLICATE);
        }

        KbDocumentEntity doc = new KbDocumentEntity();
        doc.setKbId(kbId);
        doc.setUserId(currentUser.id());
        doc.setSourcePath(path);
        doc.setFileName(file.name());
        doc.setDocType(normalizeDocType(request.docType()));
        doc.setStatus("pending");
        docMapper.insert(doc);

        String lightragDocId = "kb" + kbId + "-doc" + doc.getId();
        doc.setLightragDocId(lightragDocId);

        AiKbIndexRequest indexRequest = new AiKbIndexRequest(
                lightragDocId,
                buildDownloadUrl(currentUser.id(), path),
                guessMimeType(file.name()),
                file.name(),
                doc.getDocType());
        AiKbTaskResponse task = aiServiceClient.submitKbIndex(kbId, indexRequest);
        doc.setTaskId(task.taskId());
        docMapper.updateById(doc);
        return KbDocumentResponse.of(doc);
    }

    /** 列出某知识库的文档与索引状态。 */
    public List<KbDocumentResponse> listDocuments(CurrentUser currentUser, long kbId) {
        requireOwnedKb(currentUser, kbId);
        List<KbDocumentEntity> list = docMapper.selectList(
                new LambdaQueryWrapper<KbDocumentEntity>()
                        .eq(KbDocumentEntity::getKbId, kbId)
                        .orderByDesc(KbDocumentEntity::getId));
        return list.stream().map(KbDocumentResponse::of).toList();
    }

    /** 从知识库移除文档：删 LightRAG 索引并删记录。 */
    @Transactional
    public void deleteDocument(CurrentUser currentUser, long kbId, long docId) {
        KbDocumentEntity doc = requireOwnedDocument(currentUser, kbId, docId);
        if (doc.getLightragDocId() != null) {
            aiServiceClient.deleteKbDocument(kbId, doc.getLightragDocId());
        }
        docMapper.deleteById(docId);
    }

    // ─── 问答（SSE 透传） ──────────────────────────────────────

    /** 整库问答：把 AI 服务的 SSE 流透传到 out。 */
    public void streamChat(CurrentUser currentUser, long kbId, KbChatRequest request, OutputStream out) {
        requireOwnedKb(currentUser, kbId);
        aiServiceClient.streamChat(kbId, toAiChatRequest(request, null), out);
    }

    /** 单文档问答：带 docId 走过滤问答。 */
    public void streamDocumentChat(CurrentUser currentUser, long kbId, long docId,
                                   KbChatRequest request, OutputStream out) {
        KbDocumentEntity doc = requireOwnedDocument(currentUser, kbId, docId);
        aiServiceClient.streamChat(kbId, toAiChatRequest(request, doc.getLightragDocId()), out);
    }

    // ─── 内部工具 ──────────────────────────────────────────────

    /** 加载并校验归属的知识库，缺失/越权统一按未找到处理。 */
    private KbKnowledgeBaseEntity requireOwnedKb(CurrentUser currentUser, long kbId) {
        KbKnowledgeBaseEntity kb = kbMapper.selectById(kbId);
        if (kb == null || !kb.getUserId().equals(currentUser.id())) {
            throw new BusinessException(ErrorCode.KB_NOT_FOUND);
        }
        return kb;
    }

    /** 加载并校验归属的文档（先校验 KB 归属，再校验文档属于该 KB）。 */
    private KbDocumentEntity requireOwnedDocument(CurrentUser currentUser, long kbId, long docId) {
        requireOwnedKb(currentUser, kbId);
        KbDocumentEntity doc = docMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new BusinessException(ErrorCode.KB_DOCUMENT_NOT_FOUND);
        }
        return doc;
    }

    /** 查找同 KB 内同路径文档（去重判断），无则返回 null。 */
    private KbDocumentEntity findDocument(long kbId, String sourcePath) {
        return docMapper.selectOne(new LambdaQueryWrapper<KbDocumentEntity>()
                .eq(KbDocumentEntity::getKbId, kbId)
                .eq(KbDocumentEntity::getSourcePath, sourcePath)
                .last("LIMIT 1"));
    }

    /** 构造供 AI 服务回连下载的内部 URL（带短期签名，由 P4 的内部下载接口校验）。 */
    private String buildDownloadUrl(long ownerId, String path) {
        String sign = downloadSignService.sign(path);
        return internalBaseUrl + "/internal/kb-download"
                + "?path=" + urlEncode(path)
                + "&userId=" + ownerId
                + "&sign=" + urlEncode(sign);
    }

    private AiKbChatRequest toAiChatRequest(KbChatRequest request, String docId) {
        List<AiKbChatRequest.AiKbChatMessage> history = request.history() == null ? List.of()
                : request.history().stream()
                .map(m -> new AiKbChatRequest.AiKbChatMessage(m.role(), m.content()))
                .toList();
        return new AiKbChatRequest(request.question(), docId, history);
    }

    private String normalizeDocType(String docType) {
        return docType != null && DOC_TYPES.contains(docType) ? docType : "paper";
    }

    private String guessMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "text/markdown";
        }
        return "application/octet-stream";
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
