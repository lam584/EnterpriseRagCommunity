package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PortalReportsService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private UsersRepository usersRepository;

    @Autowired
    private ModerationQueueRepository moderationQueueRepository;

    @Autowired
    private ModerationActionsRepository moderationActionsRepository;

    @Autowired
    private ModerationPipelineRunRepository moderationPipelineRunRepository;

    @Autowired
    private ModerationConfidenceFallbackConfigRepository fallbackConfigRepository;

    @Autowired
    private ModerationPolicyConfigRepository policyConfigRepository;

    @Autowired
    private ModerationRuleAutoRunner moderationRuleAutoRunner;

    @Autowired
    private ModerationVecAutoRunner moderationVecAutoRunner;

    @Autowired
    private ModerationLlmAutoRunner moderationLlmAutoRunner;

    @Autowired(required = false)
    private ModerationAutoKickService moderationAutoKickService;

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

    @Override
    @Transactional
    public ReportSubmitResult reportProfile(Long userId, String reasonCode, String reasonText) {
        if (userId == null) throw new IllegalArgumentException("userId 不能为空");

        UsersEntity u = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        if (Boolean.TRUE.equals(u.getIsDeleted())) throw new IllegalArgumentException("用户已删除: " + userId);

        ReportsEntity rep = saveReport(ReportTargetType.PROFILE, userId, reasonCode, reasonText);
        ModerationQueueEntity q = ensureEnqueuedReport(ContentType.PROFILE, userId);
        tryWriteReportSnapshot(q, rep, snapshotProfileFields(u));
        applyReportQueueRouting(rep, q);
        return new ReportSubmitResult(rep.getId(), q.getId());
    }

    private Map<String, Object> snapshotProfileFields(UsersEntity u) {
        if (u == null) return Map.of();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("user_id", u.getId());
        out.put("username", u.getUsername());
        Map<String, Object> md = u.getMetadata();
        Object p0 = md == null ? null : md.get("profile");
        if (p0 instanceof Map<?, ?> p) {
            Object avatarUrl = p.get("avatarUrl");
            Object bio = p.get("bio");
            Object location = p.get("location");
            Object website = p.get("website");
            if (avatarUrl != null) out.put("avatarUrl", avatarUrl);
            if (bio != null) out.put("bio", bio);
            if (location != null) out.put("location", location);
            if (website != null) out.put("website", website);
        }
        return out;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
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
                    e.setReviewStage("reported");
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
        q.setReviewStage("reported");

        LocalDateTime now = LocalDateTime.now();
        boolean toHuman = false;
        try {
            ModerationPolicyConfigEntity policy = policyConfigRepository.findByContentType(q.getContentType()).orElse(null);
            Map<String, Object> cfg = policy == null ? null : policy.getConfig();
            Map<String, Object> trigger = (cfg == null || !(cfg.get("review_trigger") instanceof Map<?, ?>)) ? null : castToStringKeyMap(cfg.get("review_trigger"));
            if (trigger != null) {
                int windowMinutes = asIntOrDefault(trigger.get("window_minutes"), 10);
                if (windowMinutes < 1) windowMinutes = 1;
                LocalDateTime after = now.minusMinutes(windowMinutes);

                long total = reportsRepository.countByTargetTypeAndTargetIdAndCreatedAtAfter(rep.getTargetType(), rep.getTargetId(), after);
                long unique = reportsRepository.countDistinctReporterIdByTargetTypeAndTargetIdAndCreatedAtAfter(rep.getTargetType(), rep.getTargetId(), after);
                List<ReportsEntity> reportsInWindow = reportsRepository.findAllByTargetTypeAndTargetIdAndCreatedAtAfter(rep.getTargetType(), rep.getTargetId(), after);
                long velocity = reportsInWindow == null ? 0 : reportsInWindow.size();
                double reporterTrustAgg = buildReporterTrustAgg(reportsInWindow);

                Map<String, Object> urgent = castToStringKeyMap(trigger.get("urgent"));
                Map<String, Object> standard = castToStringKeyMap(trigger.get("standard"));
                Map<String, Object> light = castToStringKeyMap(trigger.get("light"));

                if (hitTrigger(urgent, total, unique, velocity, reporterTrustAgg)
                        || hitTrigger(standard, total, unique, velocity, reporterTrustAgg)
                        || hitTrigger(light, total, unique, velocity, reporterTrustAgg)) {
                    toHuman = true;
                }
            }
        } catch (Exception ignore) {
        }

        if (!toHuman) {
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
            toHuman = reportCount >= threshold;
        }

        if (toHuman) {
            q.setStatus(QueueStatus.HUMAN);
            q.setCurrentStage(QueueStage.HUMAN);
            q.setAssignedToId(null);
            q.setLockedBy(null);
            q.setLockedAt(null);
            q.setFinishedAt(null);
            q.setUpdatedAt(now);
            moderationQueueRepository.save(q);
        } else {
            int updated = moderationQueueRepository.requeueToAutoWithReviewStage(q.getId(), QueueStatus.PENDING, QueueStage.RULE, "reported", now);
            if (updated <= 0) throw new IllegalStateException("重新进入自动审核失败: queueId=" + q.getId());
            q.setStatus(QueueStatus.PENDING);
            q.setCurrentStage(QueueStage.RULE);
            q.setReviewStage("reported");
        }

        try {
            sealRunningPipelineRun(q.getId());
        } catch (Exception ignore) {
        }

        if (q.getStatus() == QueueStatus.PENDING && q.getCurrentStage() == QueueStage.RULE) {
            scheduleModerationAutoRunAfterCommit(q.getId());
        }
    }

    private void scheduleModerationAutoRunAfterCommit(Long queueId) {
        if (queueId == null) return;
        if (moderationAutoKickService != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            moderationAutoKickService.kickQueueId(queueId);
                        } catch (Exception ignore) {
                        }
                    }
                });
                return;
            }
            try {
                moderationAutoKickService.kickQueueId(queueId);
            } catch (Exception ignore) {
            }
            return;
        }

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

    private double buildReporterTrustAgg(List<ReportsEntity> reportsInWindow) {
        if (reportsInWindow == null || reportsInWindow.isEmpty()) return Double.NaN;
        Map<Long, Double> trustCache = new HashMap<>();
        double sum = 0D;
        int count = 0;
        for (ReportsEntity r : reportsInWindow) {
            if (r == null || r.getReporterId() == null) continue;
            Long reporterId = r.getReporterId();
            Double trust = trustCache.get(reporterId);
            if (trust == null) {
                trust = 0.5D;
                try {
                    UsersEntity u = usersRepository.findById(reporterId).orElse(null);
                    if (u != null && u.getMetadata() != null) {
                        Object raw = u.getMetadata().get("trust_score");
                        if (raw instanceof Number n) {
                            trust = Math.max(0D, Math.min(1D, n.doubleValue()));
                        }
                    }
                } catch (Exception ignore) {
                }
                trustCache.put(reporterId, trust);
            }
            sum += trust;
            count += 1;
        }
        if (count <= 0) return Double.NaN;
        return sum / count;
    }

    private static boolean hitTrigger(Map<String, Object> level,
                                      long totalReports,
                                      long uniqueReporters,
                                      long velocityPerWindow,
                                      double reporterTrustAgg) {
        if (level == null || level.isEmpty()) return false;
        Integer totalMin = asPositiveIntOrNull(level.get("total_reports_min"));
        Integer uniqueMin = asPositiveIntOrNull(level.get("unique_reporters_min"));
        Integer velocityMin = asPositiveIntOrNull(level.get("velocity_min_per_window"));
        double trustMin = asDoubleOrDefault(level.get("trust_min"), Double.NaN);

        boolean hitByTotal = totalMin != null && totalReports >= totalMin;
        boolean hitByUnique = uniqueMin != null && uniqueReporters >= uniqueMin;
        boolean hitByVelocity = velocityMin != null && velocityPerWindow >= velocityMin;
        boolean hitByTrust = !Double.isNaN(trustMin) && reporterTrustAgg >= trustMin;
        return hitByTotal || hitByUnique || hitByVelocity || hitByTrust;
    }

    private static Integer asPositiveIntOrNull(Object v) {
        if (v == null) return null;
        int out;
        if (v instanceof Number n) {
            out = n.intValue();
        } else {
            try {
                out = Integer.parseInt(String.valueOf(v).trim());
            } catch (Exception ignore) {
                return null;
            }
        }
        return out >= 1 ? out : null;
    }

    private static int asIntOrDefault(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception ignore) {
            return def;
        }
    }

    private static double asDoubleOrDefault(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception ignore) {
            return def;
        }
    }

    private static Map<String, Object> castToStringKeyMap(Object v) {
        if (!(v instanceof Map<?, ?> m)) return null;
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        for (var e : m.entrySet()) {
            Object k = e.getKey();
            if (k == null) continue;
            out.put(String.valueOf(k), e.getValue());
        }
        return out;
    }

    private void tryWriteReportSnapshot(ModerationQueueEntity q, ReportsEntity rep, Map<String, Object> targetSnapshot) {
        try {
            if (q == null || q.getId() == null || rep == null || rep.getId() == null) return;
            ModerationActionsEntity a = new ModerationActionsEntity();
            a.setQueueId(q.getId());
            a.setActorUserId(rep.getReporterId());
            a.setAction(ActionType.NOTE);
            a.setReason("REPORT_SNAPSHOT");
            java.util.LinkedHashMap<String, Object> snap = new java.util.LinkedHashMap<>();
            String snapshotId = "report:" + rep.getId() + (rep.getCreatedAt() == null ? "" : (":at:" + rep.getCreatedAt().toString()));
            snap.put("content_snapshot_id", snapshotId);
            snap.put("report_id", rep.getId());
            snap.put("target_type", enumName(rep.getTargetType()));
            snap.put("target_id", rep.getTargetId());
            if (targetSnapshot != null && !targetSnapshot.isEmpty()) snap.put("target_snapshot", targetSnapshot);
            a.setSnapshot(snap);
            a.setCreatedAt(LocalDateTime.now());
            moderationActionsRepository.save(a);
        } catch (Exception ignore) {
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
        String s = (code == null ? "" : code).trim();
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

    private void sealRunningPipelineRun(Long queueId) {
        if (queueId == null) return;
        ModerationPipelineRunEntity run = null;
        try {
            run = moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(queueId).orElse(null);
        } catch (Exception ignore) {
        }
        if (run == null || run.getStatus() != ModerationPipelineRunEntity.RunStatus.RUNNING) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            run.setStatus(ModerationPipelineRunEntity.RunStatus.FAIL);
            run.setFinalDecision(ModerationPipelineRunEntity.FinalDecision.HUMAN);
            run.setErrorCode("REQUEUED");
            run.setErrorMessage("Requeued to auto");
            run.setEndedAt(now);
            if (run.getStartedAt() != null) {
                run.setTotalMs(Duration.between(run.getStartedAt(), now).toMillis());
            }
            moderationPipelineRunRepository.save(run);
        } catch (Exception ignore) {
        }
    }
}
