package com.asuka.filelist.application.share;

import com.asuka.filelist.api.request.PublicShareGetRequest;
import com.asuka.filelist.api.request.PublicShareListRequest;
import com.asuka.filelist.api.request.ShareAuthRequest;
import com.asuka.filelist.api.request.ShareCreateRequest;
import com.asuka.filelist.api.request.ShareDeleteRequest;
import com.asuka.filelist.api.request.ShareUpdateRequest;
import com.asuka.filelist.api.request.FsGetRequest;
import com.asuka.filelist.api.request.FsListRequest;
import com.asuka.filelist.api.response.FileObjectResponse;
import com.asuka.filelist.api.response.FsListResponse;
import com.asuka.filelist.api.response.PublicShareInfoResponse;
import com.asuka.filelist.api.response.ShareAuthResponse;
import com.asuka.filelist.api.response.SharePageResponse;
import com.asuka.filelist.api.response.ShareResponse;
import com.asuka.filelist.application.auth.CurrentUserService;
import com.asuka.filelist.application.auth.PasswordService;
import com.asuka.filelist.application.fs.FsApplicationService;
import com.asuka.filelist.application.fs.FsDownloadTarget;
import com.asuka.filelist.application.user.UserApplicationService;
import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.asuka.filelist.common.path.PathUtils;
import com.asuka.filelist.domain.share.Share;
import com.asuka.filelist.domain.user.User;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.persistence.entity.ShareEntity;
import com.asuka.filelist.infrastructure.persistence.mapper.ShareMapper;
import com.asuka.filelist.infrastructure.security.CurrentUser;
import com.asuka.filelist.infrastructure.security.ShareTokenService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 分享用例服务（M7）：登录态的分享 CRUD 与匿名公开访问（info/auth/list/get/link）。
 *
 * <p>公开访问无 {@link CurrentUser}，按分享记录重建"创建者只读上下文"并把访客路径夹在
 * {@code rootPath} 子树内，复用 {@link FsApplicationService} 的存储解析与读逻辑。
 */
@Service
public class ShareApplicationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareMapper shareMapper;
    private final ShareModelMapper modelMapper;
    private final PasswordService passwordService;
    private final ShareTokenService shareTokenService;
    private final FsApplicationService fsApplicationService;
    private final UserApplicationService userApplicationService;
    private final CurrentUserService currentUserService;

    public ShareApplicationService(ShareMapper shareMapper,
                                   ShareModelMapper modelMapper,
                                   PasswordService passwordService,
                                   ShareTokenService shareTokenService,
                                   FsApplicationService fsApplicationService,
                                   UserApplicationService userApplicationService,
                                   CurrentUserService currentUserService) {
        this.shareMapper = shareMapper;
        this.modelMapper = modelMapper;
        this.passwordService = passwordService;
        this.shareTokenService = shareTokenService;
        this.fsApplicationService = fsApplicationService;
        this.userApplicationService = userApplicationService;
        this.currentUserService = currentUserService;
    }

    // ─── 登录态管理 ────────────────────────────────────────────

    /**
     * 创建分享：校验 rootPath 当前用户可读并确定 isDir，生成随机 shareId。
     */
    @Transactional
    public ShareResponse create(CurrentUser currentUser, ShareCreateRequest request) {
        String rootPath = PathUtils.fixAndCleanPath(request.rootPath());
        // 复用 fs.get 完成读权限校验与目录/文件判定（无权限会抛 PERMISSION_DENIED）
        FileObjectResponse target = fsApplicationService.get(currentUser, new FsGetRequest(rootPath, null));

        ShareEntity entity = new ShareEntity();
        entity.setShareId(generateShareId());
        entity.setCreatorId(currentUser.id());
        entity.setName(resolveName(request.name(), rootPath));
        entity.setRootPath(rootPath);
        entity.setIsDir(target.isDir());
        entity.setPasswordHash(hashOrNull(request.password()));
        entity.setBurnAfterRead(Boolean.TRUE.equals(request.burnAfterRead()));
        entity.setAccessLimit(request.accessLimit() == null ? 0L : Math.max(0L, request.accessLimit()));
        entity.setAccessCount(0L);
        entity.setAllowPreview(!Boolean.FALSE.equals(request.allowPreview()));
        entity.setAllowDownload(!Boolean.FALSE.equals(request.allowDownload()));
        entity.setEnabled(true);
        entity.setExpiresAt(request.expiresAt());
        shareMapper.insert(entity);
        return modelMapper.toResponse(modelMapper.toDomain(shareMapper.selectById(entity.getId())));
    }

    /**
     * 更新分享：可空字段保持不变；password 为空串清除、非空设置。
     */
    @Transactional
    public ShareResponse update(CurrentUser currentUser, ShareUpdateRequest request) {
        ShareEntity entity = requireOwned(currentUser, request.id());
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.password() != null) {
            entity.setPasswordHash(request.password().isBlank() ? null : passwordService.hash(request.password()));
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.accessLimit() != null) {
            entity.setAccessLimit(Math.max(0L, request.accessLimit()));
        }
        if (request.burnAfterRead() != null) {
            entity.setBurnAfterRead(request.burnAfterRead());
        }
        if (request.allowPreview() != null) {
            entity.setAllowPreview(request.allowPreview());
        }
        if (request.allowDownload() != null) {
            entity.setAllowDownload(request.allowDownload());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        shareMapper.updateById(entity);
        return modelMapper.toResponse(modelMapper.toDomain(shareMapper.selectById(entity.getId())));
    }

    /**
     * 删除分享（仅所有者）。
     */
    @Transactional
    public void delete(CurrentUser currentUser, ShareDeleteRequest request) {
        requireOwned(currentUser, request.id());
        shareMapper.deleteById(request.id());
    }

    /**
     * 分页查询我的分享。
     */
    @Transactional(readOnly = true)
    public SharePageResponse list(CurrentUser currentUser, int page, int perPage) {
        LambdaQueryWrapper<ShareEntity> query = new LambdaQueryWrapper<ShareEntity>()
                .eq(ShareEntity::getCreatorId, currentUser.id())
                .orderByDesc(ShareEntity::getCreatedAt);
        IPage<ShareEntity> result = shareMapper.selectPage(new Page<>(page, perPage), query);
        List<ShareResponse> content = result.getRecords().stream()
                .map(modelMapper::toDomain)
                .map(modelMapper::toResponse)
                .toList();
        return new SharePageResponse(content, result.getTotal(), page, perPage);
    }

    // ─── 匿名公开访问 ──────────────────────────────────────────

    /**
     * 公开分享元信息：返回是否需要密码等非敏感字段。
     */
    @Transactional(readOnly = true)
    public PublicShareInfoResponse info(String shareId) {
        Share share = requireActiveShare(shareId);
        return new PublicShareInfoResponse(
                share.shareId(), share.name(), share.isDir(),
                share.hasPassword(), share.allowPreview(), share.allowDownload());
    }

    /**
     * 密码校验：通过后计访问数（受次数上限约束），按需阅后即焚，签发 shareToken。
     */
    @Transactional
    public ShareAuthResponse auth(ShareAuthRequest request) {
        Share share = requireActiveShare(request.shareId());
        if (share.hasPassword()) {
            String provided = request.password();
            if (provided == null || provided.isEmpty()) {
                throw new BusinessException(ErrorCode.SHARE_PASSWORD_REQUIRED, "Share password is required");
            }
            if (!passwordService.matches(provided, share.passwordHash())) {
                throw new BusinessException(ErrorCode.SHARE_PASSWORD_INCORRECT, "Share password is incorrect");
            }
        }
        countAccess(share);
        if (share.burnAfterRead()) {
            shareMapper.update(null, new LambdaUpdateWrapper<ShareEntity>()
                    .eq(ShareEntity::getId, share.id())
                    .set(ShareEntity::getEnabled, false));
        }
        String token = shareTokenService.issue(share.shareId());
        return new ShareAuthResponse(token, share.name(), share.isDir(), share.allowPreview(), share.allowDownload());
    }

    /**
     * 公开目录列表：校验 token，路径夹在 rootPath 内，返回相对分享根的路径。
     */
    @Transactional(readOnly = true)
    public FsListResponse listPublic(PublicShareListRequest request, String token) {
        Share share = requireTokenizedShare(request.shareId(), token);
        String visiblePath = resolveVisiblePath(share, request.subPath());
        CurrentUser creator = creatorContext(share);
        FsListResponse listed = fsApplicationService.list(creator,
                new FsListRequest(visiblePath, null, false, request.effectivePage(), request.effectivePerPage()));
        List<FileObjectResponse> content = listed.content().stream()
                .map(item -> toShareRelative(item, share.rootPath()))
                .toList();
        return new FsListResponse(content, listed.total(), listed.page(), listed.perPage(),
                listed.hasMore(), listed.readme(), listed.header(), false, listed.provider());
    }

    /**
     * 公开文件详情：校验 token，返回相对分享根的路径。
     */
    @Transactional(readOnly = true)
    public FileObjectResponse getPublic(PublicShareGetRequest request, String token) {
        Share share = requireTokenizedShare(request.shareId(), token);
        String visiblePath = resolveVisiblePath(share, request.subPath());
        CurrentUser creator = creatorContext(share);
        FileObjectResponse object = fsApplicationService.get(creator, new FsGetRequest(visiblePath, null));
        return toShareRelative(object, share.rootPath());
    }

    /**
     * 公开下载取链：校验 token 与 allowDownload，返回供下载控制器输出的目标。
     */
    @Transactional(readOnly = true)
    public FsDownloadTarget linkPublic(String shareId, String token, String subPath, LinkArgs args) {
        Share share = requireTokenizedShare(shareId, token);
        if (!share.allowDownload()) {
            throw new BusinessException(ErrorCode.SHARE_DOWNLOAD_DISABLED, "Share download is disabled");
        }
        String visiblePath = resolveVisiblePath(share, subPath);
        CurrentUser creator = creatorContext(share);
        return fsApplicationService.link(creator, visiblePath, args);
    }

    // ─── 内部辅助 ──────────────────────────────────────────────

    /**
     * 加载归属当前用户的分享实体，否则 404（不区分不存在/非本人）。
     */
    private ShareEntity requireOwned(CurrentUser currentUser, Long id) {
        ShareEntity entity = shareMapper.selectById(id);
        if (entity == null || !entity.getCreatorId().equals(currentUser.id())) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND, "Share not found");
        }
        return entity;
    }

    /**
     * 解析有效分享（存在 + 启用 + 未过期 + 未超次数），用于 info/auth。
     */
    private Share requireActiveShare(String shareId) {
        Share share = requireShare(shareId);
        if (!share.enabled()) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND, "Share not found");
        }
        if (isExpired(share)) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED, "Share has expired");
        }
        if (share.accessLimit() > 0 && share.accessCount() >= share.accessLimit()) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED, "Share access limit reached");
        }
        return share;
    }

    /**
     * 内容访问鉴权：凭已签发 token 放行（token 短期有效，作为访问凭证）。
     */
    private Share requireTokenizedShare(String shareId, String token) {
        Share share = requireShare(shareId);
        if (!shareTokenService.verify(shareId, token)) {
            throw new BusinessException(ErrorCode.SHARE_TOKEN_INVALID, "Share token is missing or invalid");
        }
        return share;
    }

    /**
     * 按 shareId 加载分享，不存在抛 404。
     */
    private Share requireShare(String shareId) {
        ShareEntity entity = shareMapper.selectOne(new LambdaQueryWrapper<ShareEntity>()
                .eq(ShareEntity::getShareId, shareId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND, "Share not found");
        }
        return modelMapper.toDomain(entity);
    }

    /**
     * 原子自增访问次数，命中次数上限则视为过期。
     */
    private void countAccess(Share share) {
        LambdaUpdateWrapper<ShareEntity> update = new LambdaUpdateWrapper<ShareEntity>()
                .eq(ShareEntity::getId, share.id())
                .eq(ShareEntity::getEnabled, true)
                .setSql("access_count = access_count + 1");
        if (share.accessLimit() > 0) {
            update.lt(ShareEntity::getAccessCount, share.accessLimit());
        }
        if (shareMapper.update(null, update) == 0) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED, "Share access limit reached");
        }
    }

    /**
     * 重建创建者只读上下文；创建者不存在或被禁用则分享失效。
     */
    private CurrentUser creatorContext(Share share) {
        User creator;
        try {
            creator = userApplicationService.requireUser(share.creatorId());
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND, "Share owner is unavailable");
        }
        if (creator.disabled()) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND, "Share owner is disabled");
        }
        return currentUserService.toCurrentUser(creator);
    }

    /**
     * 把分享相对子路径解析为创建者可见路径，并强制夹在 rootPath 子树内。
     */
    private String resolveVisiblePath(Share share, String subPath) {
        String visible = PathUtils.joinBasePath(share.rootPath(), subPath);
        if (!PathUtils.isSubPath(share.rootPath(), visible)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "Path escapes share root");
        }
        return visible;
    }

    /**
     * 将创建者可见路径改写为相对分享根的路径，并清除下载 sign（公开下载走 /sd）。
     */
    private FileObjectResponse toShareRelative(FileObjectResponse item, String rootPath) {
        String relative = stripRoot(item.path(), rootPath);
        return new FileObjectResponse(item.id(), relative, item.name(), item.size(), item.isDir(),
                item.modified(), item.created(), null, item.thumb(), item.type(), item.hashInfo(), item.storageClass());
    }

    /**
     * 去掉 rootPath 前缀得到相对路径，根本身映射为 "/"。
     */
    private String stripRoot(String path, String rootPath) {
        String root = PathUtils.fixAndCleanPath(rootPath);
        String full = PathUtils.fixAndCleanPath(path);
        if ("/".equals(root) || PathUtils.pathEquals(full, root)) {
            return "/".equals(root) ? full : "/";
        }
        if (full.startsWith(root + "/")) {
            return full.substring(root.length());
        }
        return full;
    }

    /**
     * 是否已过期。
     */
    private boolean isExpired(Share share) {
        return share.expiresAt() != null && LocalDateTime.now().isAfter(share.expiresAt());
    }

    /**
     * 生成 URL 安全的随机 shareId（约 22 字符）。
     */
    private String generateShareId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 名称缺省时取 rootPath 末段。
     */
    private String resolveName(String name, String rootPath) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String clean = PathUtils.fixAndCleanPath(rootPath);
        return "/".equals(clean) ? "/" : clean.substring(clean.lastIndexOf('/') + 1);
    }

    /**
     * 非空密码则哈希，否则 null。
     */
    private String hashOrNull(String rawPassword) {
        return rawPassword == null || rawPassword.isBlank() ? null : passwordService.hash(rawPassword);
    }
}
