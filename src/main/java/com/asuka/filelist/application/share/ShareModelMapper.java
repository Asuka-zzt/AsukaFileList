package com.asuka.filelist.application.share;

import com.asuka.filelist.api.response.ShareResponse;
import com.asuka.filelist.domain.share.Share;
import com.asuka.filelist.infrastructure.persistence.entity.ShareEntity;
import org.springframework.stereotype.Component;

/**
 * 分享实体、领域对象与响应对象转换器。
 */
@Component
public class ShareModelMapper {

    /**
     * 持久化实体转领域对象。
     */
    public Share toDomain(ShareEntity entity) {
        return new Share(
                entity.getId(),
                entity.getShareId(),
                entity.getCreatorId(),
                entity.getName(),
                entity.getRootPath(),
                Boolean.TRUE.equals(entity.getIsDir()),
                entity.getPasswordHash(),
                Boolean.TRUE.equals(entity.getBurnAfterRead()),
                entity.getAccessLimit() == null ? 0L : entity.getAccessLimit(),
                entity.getAccessCount() == null ? 0L : entity.getAccessCount(),
                !Boolean.FALSE.equals(entity.getAllowPreview()),
                !Boolean.FALSE.equals(entity.getAllowDownload()),
                !Boolean.FALSE.equals(entity.getEnabled()),
                entity.getExpiresAt(),
                entity.getCreatedAt()
        );
    }

    /**
     * 领域对象转管理端响应（不含密码哈希）。
     */
    public ShareResponse toResponse(Share share) {
        return new ShareResponse(
                share.id(),
                share.shareId(),
                share.name(),
                share.rootPath(),
                share.isDir(),
                share.hasPassword(),
                share.burnAfterRead(),
                share.accessLimit(),
                share.accessCount(),
                share.allowPreview(),
                share.allowDownload(),
                share.enabled(),
                share.expiresAt(),
                share.createdAt()
        );
    }
}
