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

    /** HUMAN 状态任务认领：只有未被认领时才能成功 */
    AdminModerationQueueDetailDTO claim(Long id);

    /** HUMAN 状态任务释放：仅允许本人释放（并发安全） */
    AdminModerationQueueDetailDTO release(Long id);

    /** 重新进入自动审核：重置为 PENDING + RULE，并清理人工认领/锁 */
    AdminModerationQueueDetailDTO requeueToAuto(Long id, String reason);

    /** 终态任务进入人工审核：重置为 HUMAN + HUMAN，并清理人工认领/锁/完成时间 */
    AdminModerationQueueDetailDTO toHuman(Long id, String reason);
}
