package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationQueueQueryDTO;
import org.springframework.data.domain.Page;

public interface AdminModerationQueueService {
    Page<AdminModerationQueueItemDTO> list(ModerationQueueQueryDTO query);

    AdminModerationQueueDetailDTO getDetail(Long id);

    AdminModerationQueueDetailDTO approve(Long id, String reason);

    AdminModerationQueueDetailDTO reject(Long id, String reason);

    /** 供发帖/评论创建时调用：确保存在一条 PENDING 队列记录 */
    void ensureEnqueuedPost(Long postId);

    void ensureEnqueuedComment(Long commentId);

    /**
     * 后台补齐历史遗留的待审核数据：从 posts/comments 表扫描 PENDING 内容，写入 moderation_queue（幂等去重）。
     */
    com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillResponse backfill(com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillRequest req);
}
