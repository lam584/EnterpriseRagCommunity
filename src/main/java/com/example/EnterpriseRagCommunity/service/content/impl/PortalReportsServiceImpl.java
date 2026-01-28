package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PortalReportsService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PortalReportsServiceImpl implements PortalReportsService {

    @Autowired
    private ReportsRepository reportsRepository;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private CommentsRepository commentsRepository;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private ModerationQueueRepository moderationQueueRepository;

    @Autowired
    private ModerationPipelineRunRepository moderationPipelineRunRepository;

    @Autowired
    private ModerationConfidenceFallbackConfigRepository fallbackConfigRepository;

    @Autowired
    private ModerationRuleAutoRunner moderationRuleAutoRunner;

    @Autowired
    private ModerationVecAutoRunner moderationVecAutoRunner;

    @Autowired
    private ModerationLlmAutoRunner moderationLlmAutoRunner;

    @Override
    @Transactional
    public ReportSubmitResult reportPost(Long postId, String reasonCode, String reasonText) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");

        PostsEntity post = postsRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + postId));
        if (Boolean.TRUE.equals(post.getIsDeleted())) throw new IllegalArgumentException("帖子已删除: " + postId);

        ReportsEntity rep = saveReport(ReportTargetType.POST, postId, reasonCode, reasonText);
        ModerationQueueEntity q = ensureEnqueuedReport(ContentType.POST, postId);
        applyReportQueueRouting(rep, q);
        return new ReportSubmitResult(rep.getId(), q.getId());
    }

    @Override
    @Transactional
    public ReportSubmitResult reportComment(Long commentId, String reasonCode, String reasonText) {
        if (commentId == null) throw new IllegalArgumentException("commentId 不能为空");

        CommentsEntity c = commentsRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + commentId));
        if (Boolean.TRUE.equals(c.getIsDeleted())) throw new IllegalArgumentException("评论已删除: " + commentId);

        ReportsEntity rep = saveReport(ReportTargetType.COMMENT, commentId, reasonCode, reasonText);
        ModerationQueueEntity q = ensureEnqueuedReport(ContentType.COMMENT, commentId);
        applyReportQueueRouting(rep, q);
        return new ReportSubmitResult(rep.getId(), q.getId());
    }

    private ReportsEntity saveReport(ReportTargetType targetType, Long targetId, String reasonCode, String reasonText) {
        Long reporterId = currentUserIdOrThrow();

        ReportsEntity rep = new ReportsEntity();
        rep.setReporterId(reporterId);
        rep.setTargetType(targetType);
        rep.setTargetId(targetId);
        rep.setReasonCode(normalizeReasonCode(reasonCode));
        rep.setReasonText(normalizeReasonText(reasonText));
        rep.setStatus(ReportStatus.PENDING);
        rep.setHandledById(null);
        rep.setHandledAt(null);
        rep.setResolution(null);
        return reportsRepository.save(rep);
    }

    private ModerationQueueEntity ensureEnqueuedReport(ContentType contentType, Long contentId) {
        return moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, contentType, contentId)
                .orElseGet(() -> {
                    ModerationQueueEntity e = new ModerationQueueEntity();
                    e.setCaseType(ModerationCaseType.REPORT);
                    e.setContentType(contentType);
                    e.setContentId(contentId);
                    e.setStatus(QueueStatus.PENDING);
                    e.setCurrentStage(QueueStage.RULE);
                    e.setPriority(0);
                    e.setAssignedToId(null);
                    e.setLockedBy(null);
                    e.setLockedAt(null);
                    e.setFinishedAt(null);
                    e.setCreatedAt(LocalDateTime.now());
                    e.setUpdatedAt(LocalDateTime.now());
                    return moderationQueueRepository.save(e);
                });
    }

    private void applyReportQueueRouting(ReportsEntity rep, ModerationQueueEntity q) {
        if (rep == null || rep.getId() == null) throw new IllegalStateException("举报写入失败");
        if (q == null || q.getId() == null) throw new IllegalStateException("举报审核任务入队失败");

        int threshold = 5;
        try {
            ModerationConfidenceFallbackConfigEntity cfg = fallbackConfigRepository.findAll().stream()
                    .max(java.util.Comparator.comparing(ModerationConfidenceFallbackConfigEntity::getUpdatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .orElse(null);
            if (cfg != null && cfg.getReportHumanThreshold() != null) threshold = cfg.getReportHumanThreshold();
        } catch (Exception ignore) {
        }
        if (threshold < 1) threshold = 1;

        long reportCount;
        try {
            reportCount = reportsRepository.countByTargetTypeAndTargetId(rep.getTargetType(), rep.getTargetId());
        } catch (Exception e) {
            reportCount = 1;
        }

        if (reportCount >= threshold) {
            q.setStatus(QueueStatus.HUMAN);
            q.setCurrentStage(QueueStage.HUMAN);
            q.setAssignedToId(null);
            q.setLockedBy(null);
            q.setLockedAt(null);
            q.setFinishedAt(null);
            q.setUpdatedAt(LocalDateTime.now());
            moderationQueueRepository.save(q);
        } else {
            int updated = moderationQueueRepository.requeueToAuto(q.getId(), QueueStatus.PENDING, QueueStage.RULE, LocalDateTime.now());
            if (updated <= 0) throw new IllegalStateException("重新进入自动审核失败: queueId=" + q.getId());
            q.setStatus(QueueStatus.PENDING);
            q.setCurrentStage(QueueStage.RULE);
        }

        try {
            moderationPipelineRunRepository.deleteAllByQueueId(q.getId());
        } catch (Exception ignore) {
        }

        if (q.getStatus() == QueueStatus.PENDING && q.getCurrentStage() == QueueStage.RULE) {
            try {
                moderationRuleAutoRunner.runOnce();
            } catch (Exception ignore) {
            }
            try {
                moderationVecAutoRunner.runOnce();
            } catch (Exception ignore) {
            }
            try {
                moderationLlmAutoRunner.runOnce();
            } catch (Exception ignore) {
            }
        }
    }

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
            };
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    private static String normalizeReasonCode(String code) {
        String s = String.valueOf(code == null ? "" : code).trim();
        if (s.isEmpty()) throw new IllegalArgumentException("reasonCode 不能为空");
        s = s.toUpperCase(Locale.ROOT);
        if (s.length() > 64) s = s.substring(0, 64);
        return s;
    }

    private static String normalizeReasonText(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return null;
        if (s.length() > 255) s = s.substring(0, 255);
        return s;
    }
}
