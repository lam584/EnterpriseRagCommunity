package com.example.EnterpriseRagCommunity.service.moderation.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.moderation.web.WebContentFetchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

record PromptVars(String title, String content, String text) {}

record QueueCtx(
        ModerationQueueEntity queue,
        ModerationPolicyConfigEntity policy,
        String policyVersion,
        Map<String, Object> policyConfig
) {}

record RiskTagItem(String slug, String name) {}

@Component
@RequiredArgsConstructor
class AdminModerationLlmContextBuilder {

    private final ModerationQueueRepository queueRepository;
    private final ModerationPolicyConfigRepository policyConfigRepository;
    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final ReportsRepository reportsRepository;
    private final ModerationActionsRepository moderationActionsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final UsersRepository usersRepository;
    private final TagsRepository tagsRepository;
    private final WebContentFetchService webContentFetchService;
    private final AdminModerationLlmImageSupport imageSupport;

    private final ObjectMapper objectMapper = new ObjectMapper();

    PromptVars resolvePromptVarsSafe(LlmModerationTestRequest req) {
        if (req == null) return null;
        if (req.getText() != null && !req.getText().isBlank()) {
            String content = req.getText();
            return new PromptVars("", content, content);
        }
        if (req.getQueueId() == null) return null;

        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null) return null;

        if (q.getContentType() == ContentType.POST) {
            var p = postsRepository.findById(q.getContentId()).orElse(null);
            if (p == null) return null;
            String title = p.getTitle() == null ? "" : p.getTitle();
            String content = p.getContent() == null ? "" : p.getContent();
            String files = buildPostFilesBlock(q.getContentId());
            String web = buildPostWebBlock(q.getContentId(), content);
            String base = ("[POST]\n标题: " + title + "\n内容: " + content
                    + (files == null || files.isBlank() ? "" : ("\n\n" + files))
                    + (web == null || web.isBlank() ? "" : ("\n\n" + web))
            ).trim();
            String contentNormalized = (content
                    + (files == null || files.isBlank() ? "" : ("\n\n" + files))
                    + (web == null || web.isBlank() ? "" : ("\n\n" + web))
            ).trim();
            return new PromptVars(title, contentNormalized, base);
        }
        if (q.getContentType() == ContentType.COMMENT) {
            var c = commentsRepository.findById(q.getContentId()).orElse(null);
            if (c == null) return null;
            String content = c.getContent() == null ? "" : c.getContent();
            String base = ("[COMMENT]\n内容: " + content).trim();
            return new PromptVars("", content.trim(), base);
        }
        if (q.getContentType() == ContentType.PROFILE) {
            UsersEntity u = usersRepository.findById(q.getContentId()).orElse(null);
            if (u == null) return null;
            Map<String, Object> meta = u.getMetadata();
            String reviewStage = blankToNull(req.getReviewStage());
            Map<String, Object> profile = null;
            if (q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.REPORT
                    && "reported".equalsIgnoreCase(reviewStage)) {
                profile = resolveReportProfileSnapshotFields(q);
            }
            if (profile == null
                    && q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.CONTENT) {
                profile = asMap(meta == null ? null : meta.get("profilePending"));
            }
            if (profile == null) profile = asMap(meta == null ? null : meta.get("profile"));

            String username = safeString(profile == null ? null : profile.get("username"));
            if (username == null) username = nullToEmpty(safeString(u.getUsername()));

            String bio = profile == null ? "" : nullToEmpty(safeString(profile.get("bio")));
            String location = profile == null ? "" : nullToEmpty(safeString(profile.get("location")));
            String website = profile == null ? "" : nullToEmpty(safeString(profile.get("website")));
            String avatarUrl = profile == null ? "" : nullToEmpty(safeString(profile.get("avatarUrl")));

            String content = ("username: " + username
                    + "\nbio: " + bio
                    + "\nlocation: " + location
                    + "\nwebsite: " + website
                    + "\navatarUrl: " + avatarUrl).trim();
            String base = ("[PROFILE]\n" + content).trim();
            return new PromptVars(username, content, base);
        }

        return null;
    }

    PromptVars resolvePromptVars(LlmModerationTestRequest req) {
        if (req == null) return null;
        if (req.getText() != null && !req.getText().isBlank()) {
            String content = req.getText();
            return new PromptVars("", content, content);
        }
        if (req.getQueueId() == null) return null;

        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null) return null;

        if (q.getContentType() == ContentType.POST) {
            var p = postsRepository.findById(q.getContentId()).orElse(null);
            if (p == null) return null;
            String title = p.getTitle() == null ? "" : p.getTitle();
            String content = p.getContent() == null ? "" : p.getContent();
            String files = buildPostFilesBlock(q.getContentId());
            String web = buildPostWebBlock(q.getContentId(), content);
            String base = ("[POST]\n标题: " + title + "\n内容: " + content
                    + (files == null || files.isBlank() ? "" : ("\n\n" + files))
                    + (web == null || web.isBlank() ? "" : ("\n\n" + web))
            ).trim();
            String reports = buildReportsBlock(ReportTargetType.POST, q.getContentId());
            String text = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + base).trim() : base;
            String contentWithReports = (reports != null && !reports.isBlank())
                    ? (reports + "\n\n" + content
                    + (files == null || files.isBlank() ? "" : ("\n\n" + files))
                    + (web == null || web.isBlank() ? "" : ("\n\n" + web))
            ).trim()
                    : (content
                    + (files == null || files.isBlank() ? "" : ("\n\n" + files))
                    + (web == null || web.isBlank() ? "" : ("\n\n" + web))
            );
            return new PromptVars(title, contentWithReports, text);
        }
        if (q.getContentType() == ContentType.COMMENT) {
            var c = commentsRepository.findById(q.getContentId()).orElse(null);
            if (c == null) return null;
            String content = c.getContent() == null ? "" : c.getContent();
            String base = ("[COMMENT]\n内容: " + content).trim();
            String reports = buildReportsBlock(ReportTargetType.COMMENT, q.getContentId());
            String text = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + base).trim() : base;
            String contentWithReports = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + content).trim() : content;
            return new PromptVars("", contentWithReports, text);
        }
        if (q.getContentType() == ContentType.PROFILE) {
            UsersEntity u = usersRepository.findById(q.getContentId()).orElse(null);
            if (u == null) return null;
            Map<String, Object> meta = u.getMetadata();
            Map<String, Object> profile = null;
            String reviewStage = blankToNull(req.getReviewStage());
            if (q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.REPORT
                    && "reported".equalsIgnoreCase(reviewStage)) {
                profile = resolveReportProfileSnapshotFields(q);
            }
            if (profile == null
                    && q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.CONTENT) {
                profile = asMap(meta == null ? null : meta.get("profilePending"));
            }
            if (profile == null) profile = asMap(meta == null ? null : meta.get("profile"));

            String username = safeString(profile == null ? null : profile.get("username"));
            if (username == null || username.isBlank()) username = u.getUsername() == null ? "" : u.getUsername();
            String bio = profile == null ? "" : nullToEmpty(safeString(profile.get("bio")));
            String location = profile == null ? "" : nullToEmpty(safeString(profile.get("location")));
            String website = profile == null ? "" : nullToEmpty(safeString(profile.get("website")));
            String avatarUrl = profile == null ? "" : nullToEmpty(safeString(profile.get("avatarUrl")));

            String content = ("username: " + username
                    + "\nbio: " + bio
                    + "\nlocation: " + location
                    + "\nwebsite: " + website
                    + "\navatarUrl: " + avatarUrl).trim();
            String base = ("[PROFILE]\n" + content).trim();
            String reports = buildReportsBlock(ReportTargetType.PROFILE, q.getContentId());
            String text = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + base).trim() : base;
            String contentWithReports = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + content).trim() : content;
            return new PromptVars(username, contentWithReports, text);
        }

        return null;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String buildSyntheticSnapshotId(ModerationQueueEntity q) {
        if (q == null || q.getId() == null || q.getContentType() == null || q.getContentId() == null) return null;
        String t = q.getUpdatedAt() == null ? "" : q.getUpdatedAt().toString();
        return "moderation:" + q.getContentType().name().toLowerCase(Locale.ROOT) + ":" + q.getContentId() + ":queue:" + q.getId() + (t.isBlank() ? "" : (":at:" + t));
    }

    String buildPolicyContextBlock(LlmModerationTestRequest req, boolean useQueue) {
        if (!useQueue) return null;
        if (req == null || req.getQueueId() == null) return null;
        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null || q.getContentType() == null) return null;
        var policy = policyConfigRepository.findByContentType(q.getContentType()).orElse(null);
        if (policy == null || policy.getPolicyVersion() == null || policy.getPolicyVersion().isBlank()) return null;
        String json;
        try {
            json = objectMapper.writeValueAsString(policy.getConfig());
        } catch (Exception e) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[POLICY]\n");
        sb.append("policy_version=").append(policy.getPolicyVersion().trim()).append('\n');
        sb.append("content_type=").append(q.getContentType()).append('\n');
        if (req.getReviewStage() != null && !req.getReviewStage().isBlank()) {
            sb.append("review_stage=").append(req.getReviewStage().trim()).append('\n');
        }
        sb.append("policy_json=").append(json);
        return sb.toString();
    }

    QueueCtx resolveQueueCtx(LlmModerationTestRequest req, boolean useQueue) {
        if (!useQueue) return null;
        if (req == null || req.getQueueId() == null) return null;
        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null || q.getContentType() == null || q.getContentId() == null) return null;
        ModerationPolicyConfigEntity policy = policyConfigRepository.findByContentType(q.getContentType()).orElse(null);
        String pv = policy == null ? null : blankToNull(policy.getPolicyVersion());
        Map<String, Object> pc = policy == null ? null : policy.getConfig();
        if (pc == null) pc = Map.of();
        return new QueueCtx(q, policy, pv, pc);
    }

    Map<String, Object> resolveUserContext(QueueCtx ctx) {
        if (ctx == null || ctx.queue() == null) return null;
        Long userId = null;
        if (ctx.queue().getContentType() == ContentType.PROFILE) {
            userId = ctx.queue().getContentId();
        } else if (ctx.queue().getContentType() == ContentType.POST) {
            var p = postsRepository.findById(ctx.queue().getContentId()).orElse(null);
            if (p != null) userId = p.getAuthorId();
        } else if (ctx.queue().getContentType() == ContentType.COMMENT) {
            var c = commentsRepository.findById(ctx.queue().getContentId()).orElse(null);
            if (c != null) userId = c.getAuthorId();
        }

        if (userId == null) return null;
        var u = usersRepository.findById(userId).orElse(null);
        if (u == null) return null;

        Map<String, Object> uc = new LinkedHashMap<>();
        if (u.getCreatedAt() != null) {
            uc.put("is_new_user", u.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24)));
        }
        if (u.getMetadata() != null) {
            Object rs = deepGet(u.getMetadata(), "risk_score");
            if (rs instanceof Number n) uc.put("risk_score", n.doubleValue());
        }
        if (u.getMetadata() != null) {
            String d = safeString(u.getMetadata().get("domain"));
            if (d != null) uc.put("domain", d);
        }
        return uc.isEmpty() ? null : uc;
    }

    String buildQueueTraceLine(LlmModerationTestRequest req) {
        if (req == null || req.getQueueId() == null) return null;
        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null) return null;

        Long postId = null;
        List<Long> fileAssetIds = new ArrayList<>();

        if (q.getContentType() == ContentType.POST) {
            postId = q.getContentId();
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                    0,
                    50,
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("createdAt"), org.springframework.data.domain.Sort.Order.asc("id"))
            );
            var page = postAttachmentsRepository.findByPostId(postId, pageable);
            if (page != null) {
                page.getContent();
                for (var a : page.getContent()) {
                    if (a == null || a.getFileAssetId() == null) continue;
                    fileAssetIds.add(a.getFileAssetId());
                }
            }
        }

        Map<Long, String> extractionStatusById = new HashMap<>();
        Map<Long, Integer> extractedCharsById = new HashMap<>();
        if (!fileAssetIds.isEmpty()) {
            try {
                for (var ex : fileAssetExtractionsRepository.findAllById(fileAssetIds)) {
                    if (ex == null || ex.getFileAssetId() == null) continue;
                    String st = enumName(ex.getExtractStatus());
                    extractionStatusById.put(ex.getFileAssetId(), st == null ? "UNKNOWN" : st);
                    String t = ex.getExtractedText();
                    if (t == null) t = "";
                    extractedCharsById.put(ex.getFileAssetId(), t.length());
                }
            } catch (Exception ignore) {
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("TRACE queueId=").append(q.getId());
        if (postId != null) sb.append(" postId=").append(postId);
        if (!fileAssetIds.isEmpty()) sb.append(" fileAssetIds=").append(fileAssetIds);
        if (!fileAssetIds.isEmpty()) {
            sb.append(" fileAssets=");
            int take = Math.min(20, fileAssetIds.size());
            sb.append('[');
            for (int i = 0; i < take; i++) {
                Long id = fileAssetIds.get(i);
                if (id == null) continue;
                String st = extractionStatusById.get(id);
                if (st == null) st = "MISSING";
                Integer chars = extractedCharsById.get(id);
                if (chars == null) chars = 0;
                if (i > 0) sb.append(',');
                sb.append("{id=").append(id).append(",st=").append(st).append(",chars=").append(chars).append('}');
            }
            if (fileAssetIds.size() > take) sb.append(",...");
            sb.append(']');
        }
        return sb.toString();
    }

    private List<RiskTagItem> resolveActiveRiskTagItems() {
        try {
            return tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK).stream()
                    .filter(t -> t != null && t.getSlug() != null && !t.getSlug().isBlank())
                    .map(t -> new RiskTagItem(
                            t.getSlug().trim(),
                            (t.getName() == null || t.getName().isBlank()) ? t.getSlug().trim() : t.getName().trim()
                    ))
                    .distinct()
                    .toList();
        } catch (Exception ignore) {
            return List.of();
        }
    }

    String buildTextAuditInputJson(LlmModerationTestRequest req, PromptVars vars, QueueCtx ctx) {
        if (ctx == null || ctx.queue() == null || ctx.queue().getContentType() == null || ctx.queue().getContentId() == null) return null;
        if (ctx.policyVersion() == null || ctx.policyVersion().isBlank()) return null;
        Map<String, Object> labelTax = new LinkedHashMap<>();
        List<RiskTagItem> dbTags = resolveActiveRiskTagItems();
        if (!dbTags.isEmpty()) {
            labelTax.put("taxonomy_id", "risk_tags");
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            List<Map<String, Object>> labelMap = new ArrayList<>();
            for (RiskTagItem it : dbTags) {
                if (it == null) continue;
                String name = it.name();
                if (name == null || name.isBlank()) continue;
                allowed.add(name);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("slug", it.slug());
                m.put("name", name);
                labelMap.add(m);
            }
            if (!allowed.isEmpty()) labelTax.put("allowed_labels", new ArrayList<>(allowed));
            if (!labelMap.isEmpty()) labelTax.put("label_map", labelMap);
        }

        ModerationQueueEntity q = ctx.queue();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("task", "TextAudit");
        root.put("policy_version", ctx.policyVersion());
        if (!labelTax.isEmpty()) root.put("label_taxonomy", labelTax);

        String reviewStage = blankToNull(req == null ? null : req.getReviewStage());
        if (reviewStage != null) root.put("review_stage", reviewStage);

        Map<String, Object> userContext = resolveUserContext(ctx);
        if (userContext != null) root.put("user_context", userContext);

        if (q.getContentType() == ContentType.POST) {
            root.put("post_id", q.getContentId());
            root.put("mode", "full");
            if (vars != null && vars.title() != null && !vars.title().isBlank()) root.put("title", vars.title());
            if (vars != null && vars.content() != null && !vars.content().isBlank()) root.put("text", vars.content());
            root.put("related_ocr", resolveRelatedOcr(ctx));
            if ("reported".equalsIgnoreCase(reviewStage)) {
                Map<String, Object> rc = buildReportContext(ReportTargetType.POST, q.getContentId(), ctx, reviewStage);
                if (rc != null && !rc.isEmpty()) root.put("report_context", rc);
            }
        } else if (q.getContentType() == ContentType.COMMENT) {
            var c = commentsRepository.findById(q.getContentId()).orElse(null);
            if (c != null) {
                root.put("comment_id", c.getId());
                root.put("post_id", c.getPostId());
                root.put("author_id", c.getAuthorId());
                if (c.getParentId() != null) root.put("parent_comment_id", c.getParentId());
                if (reviewStage == null) root.put("comment_stage", "publish");
                if (vars != null && vars.content() != null && !vars.content().isBlank()) root.put("text", vars.content());
                Map<String, Object> threadContext = buildThreadContextForComment(c.getPostId(), c.getParentId());
                if (threadContext != null && !threadContext.isEmpty()) root.put("thread_context", threadContext);
                root.put("related_ocr", resolveRelatedOcr(ctx));
                if ("reported".equalsIgnoreCase(reviewStage)) {
                    Map<String, Object> rc = buildReportContext(ReportTargetType.COMMENT, q.getContentId(), ctx, reviewStage);
                    if (rc != null && !rc.isEmpty()) root.put("report_context", rc);
                }
            }
        } else if (q.getContentType() == ContentType.PROFILE) {
            UsersEntity u = usersRepository.findById(q.getContentId()).orElse(null);
            if (u != null) {
                root.put("user_id", u.getId());
                if (reviewStage == null) root.put("profile_stage", "update");
                Map<String, Object> meta = u.getMetadata();
                Map<String, Object> publicProfile = asMap(meta == null ? null : meta.get("profile"));

                Map<String, Object> effective = null;
                if (q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.REPORT
                        && "reported".equalsIgnoreCase(reviewStage)) {
                    effective = resolveReportProfileSnapshotFields(q);
                }
                if (effective == null
                        && q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.CONTENT) {
                    effective = asMap(meta == null ? null : meta.get("profilePending"));
                }
                if (effective == null) effective = publicProfile;

                String username = safeString(effective == null ? null : effective.get("username"));
                if (username == null) username = safeString(u.getUsername());

                String bio = nullToEmpty(safeString(effective == null ? null : effective.get("bio")));
                String location = nullToEmpty(safeString(effective == null ? null : effective.get("location")));
                String website = nullToEmpty(safeString(effective == null ? null : effective.get("website")));
                String avatarUrl = nullToEmpty(safeString(effective == null ? null : effective.get("avatarUrl")));

                Map<String, Object> profileFields = new LinkedHashMap<>();
                profileFields.put("username", nullToEmpty(username));
                profileFields.put("bio", bio);
                profileFields.put("location", location);
                profileFields.put("website", website);
                profileFields.put("avatarUrl", avatarUrl);
                root.put("profile_fields", profileFields);

                if (q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.CONTENT) {
                    String oldUsername = safeString(u.getUsername());
                    String oldBio = nullToEmpty(safeString(publicProfile == null ? null : publicProfile.get("bio")));
                    String oldLocation = nullToEmpty(safeString(publicProfile == null ? null : publicProfile.get("location")));
                    String oldWebsite = nullToEmpty(safeString(publicProfile == null ? null : publicProfile.get("website")));
                    String oldAvatarUrl = nullToEmpty(safeString(publicProfile == null ? null : publicProfile.get("avatarUrl")));
                    Map<String, Object> oldProfileFields = new LinkedHashMap<>();
                    oldProfileFields.put("username", nullToEmpty(oldUsername));
                    oldProfileFields.put("bio", oldBio);
                    oldProfileFields.put("location", oldLocation);
                    oldProfileFields.put("website", oldWebsite);
                    oldProfileFields.put("avatarUrl", oldAvatarUrl);
                    root.put("old_profile_fields", oldProfileFields);
                }
                root.put("related_ocr", resolveRelatedOcr(ctx));
                if ("reported".equalsIgnoreCase(reviewStage)) {
                    Map<String, Object> rc = buildReportContext(ReportTargetType.PROFILE, q.getContentId(), ctx, reviewStage);
                    if (rc != null && !rc.isEmpty()) root.put("report_context", rc);
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return null;
        }
    }

    String buildVisionAuditInputJsonList(LlmModerationTestRequest req, QueueCtx ctx, List<ImageRef> images) {
        if (ctx == null || ctx.queue() == null || ctx.queue().getContentType() == null || ctx.queue().getContentId() == null) return null;
        if (ctx.policyVersion() == null || ctx.policyVersion().isBlank()) return null;
        Map<String, Object> labelTax = new LinkedHashMap<>();
        List<RiskTagItem> dbTags = resolveActiveRiskTagItems();
        if (!dbTags.isEmpty()) {
            labelTax.put("taxonomy_id", "risk_tags");
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            List<Map<String, Object>> labelMap = new ArrayList<>();
            for (RiskTagItem it : dbTags) {
                if (it == null) continue;
                String name = it.name();
                if (name == null || name.isBlank()) continue;
                allowed.add(name);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("slug", it.slug());
                m.put("name", name);
                labelMap.add(m);
            }
            if (!allowed.isEmpty()) labelTax.put("allowed_labels", new ArrayList<>(allowed));
            if (!labelMap.isEmpty()) labelTax.put("label_map", labelMap);
        }

        String contentType = ctx.queue().getContentType().name().toLowerCase(Locale.ROOT);
        Long contentId = ctx.queue().getContentId();

        List<Map<String, Object>> items = new ArrayList<>();
        if (images != null) {
            int idx = 1;
            for (ImageRef img : images) {
                if (img == null) continue;
                String url = blankToNull(img.url());
                if (url == null) continue;
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("task", "VisionAudit");
                it.put("policy_version", ctx.policyVersion());
                if (!labelTax.isEmpty()) it.put("label_taxonomy", labelTax);
                it.put("content_type", contentType);
                it.put("content_id", String.valueOf(contentId));
                it.put("image_id", "img_" + idx);
                it.put("image", url);
                items.add(it);
                idx += 1;
                if (items.size() >= 50) break;
            }
        }
        if (items.isEmpty()) return null;

        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return null;
        }
    }

    String buildJudgeInputJson(
            QueueCtx ctx,
            String content,
            String imageDescription,
            Double textScore,
            Double imageScore,
            List<String> textReasons,
            List<String> imageReasons,
            List<String> textEvidence,
            List<String> imageEvidence,
            String contentType,
            Long contentId
    ) {
        if (ctx == null || ctx.policyVersion() == null) return null;

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("task", "Judge");
        root.put("policy_version", ctx.policyVersion());

        Map<String, Object> policyConfig = ctx.policyConfig() == null ? Map.of() : ctx.policyConfig();
        if (policyConfig.containsKey("thresholds")) root.put("thresholds", policyConfig.get("thresholds"));
        if (policyConfig.containsKey("escalate_rules")) root.put("escalate_rules", policyConfig.get("escalate_rules"));
        List<RiskTagItem> dbTags = resolveActiveRiskTagItems();
        if (!dbTags.isEmpty()) {
            Map<String, Object> labelTax = new LinkedHashMap<>();
            labelTax.put("taxonomy_id", "risk_tags");
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            List<Map<String, Object>> labelMap = new ArrayList<>();
            for (RiskTagItem it : dbTags) {
                if (it == null) continue;
                String name = it.name();
                if (name == null || name.isBlank()) continue;
                allowed.add(name);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("slug", it.slug());
                m.put("name", name);
                labelMap.add(m);
            }
            if (!allowed.isEmpty()) labelTax.put("allowed_labels", new ArrayList<>(allowed));
            if (!labelMap.isEmpty()) labelTax.put("label_map", labelMap);
            if (labelTax.containsKey("allowed_labels")) root.put("label_taxonomy", labelTax);
        }

        root.put("judge_mode", (imageScore != null && imageScore > 0) ? "multimodal" : "text_only");
        root.put("content_type", contentType == null ? "post" : contentType.toLowerCase(Locale.ROOT));
        root.put("content_id", String.valueOf(contentId));

        List<Map<String, Object>> textResults = new ArrayList<>();
        Map<String, Object> tr = new LinkedHashMap<>();
        tr.put("risk_score", textScore == null ? 0.0 : textScore);
        if (textReasons != null && !textReasons.isEmpty()) tr.put("reasons", textReasons);
        if (textEvidence != null && !textEvidence.isEmpty()) tr.put("evidence", textEvidence);
        textResults.add(tr);
        root.put("text_results", textResults);

        List<Map<String, Object>> imageResults = new ArrayList<>();
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("risk_score", imageScore == null ? 0.0 : imageScore);
        if (imageDescription != null) ir.put("description", imageDescription);
        if (imageReasons != null && !imageReasons.isEmpty()) ir.put("reasons", imageReasons);
        if (imageEvidence != null && !imageEvidence.isEmpty()) ir.put("evidence", imageEvidence);
        imageResults.add(ir);
        root.put("image_results", imageResults);

        Map<String, Object> eb = new LinkedHashMap<>();
        eb.put("items", new ArrayList<>());
        eb.put("open_questions", new ArrayList<>());
        root.put("evidence_book", eb);

        Map<String, Object> gm = new LinkedHashMap<>();
        gm.put("text", content == null ? "" : content);
        gm.put("source", "extractive");
        gm.put("uncertainty", 0.0);
        gm.put("token_budget", 0);
        root.put("global_memory", gm);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildThreadContextForComment(Long postId, Long repliedCommentId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (postId != null) {
            var p = postsRepository.findById(postId).orElse(null);
            if (p != null) {
                String title = p.getTitle() == null ? null : p.getTitle().trim();
                if (title != null && !title.isBlank()) out.put("post_title", title);
                String content = p.getContent();
                if (content != null) {
                    String t = content.trim();
                    if (t.length() > 400) t = t.substring(0, 400);
                    if (!t.isBlank()) out.put("post_excerpt", t);
                }
            }
        }
        if (repliedCommentId != null) {
            var c = commentsRepository.findById(repliedCommentId).orElse(null);
            if (c != null && c.getContent() != null) {
                String t = c.getContent().trim();
                if (t.length() > 400) t = t.substring(0, 400);
                if (!t.isBlank()) out.put("replied_comment_excerpt", t);
            }
        }
        return out.isEmpty() ? null : out;
    }

    String buildReportsBlock(ReportTargetType targetType, Long targetId) {
        if (targetType == null || targetId == null) return null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0,
                10,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("id"))
        );
        var page = reportsRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
        if (page.getContent().isEmpty()) return null;

        List<String> lines = new ArrayList<>();
        for (var r : page.getContent()) {
            if (r == null) continue;
            if (r.getStatus() != ReportStatus.PENDING && r.getStatus() != ReportStatus.REVIEWING) continue;
            String code = r.getReasonCode() == null ? "" : r.getReasonCode().trim();
            String text = r.getReasonText() == null ? "" : r.getReasonText().trim();
            if (code.isEmpty() && text.isEmpty()) continue;
            if (!text.isEmpty()) {
                lines.add("- " + code + ": " + text);
            } else {
                lines.add("- " + code);
            }
            if (lines.size() >= 3) break;
        }
        if (lines.isEmpty()) return null;
        return ("[REPORTS]\n" + String.join("\n", lines)).trim();
    }

    List<Map<String, Object>> resolveRelatedOcr(QueueCtx ctx) {
        if (ctx == null || ctx.queue() == null) return List.of();
        Long contentId = ctx.queue().getContentId();
        if (contentId == null) return List.of();

        List<Map<String, Object>> ocrs = new ArrayList<>();

        if (ctx.queue().getContentType() == ContentType.POST) {
            try {
                var page = postAttachmentsRepository.findByPostId(
                        contentId,
                        PageRequest.of(0, 50, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
                );
                if (page != null) {
                    page.getContent();
                    List<Long> faIds = new ArrayList<>();
                    for (var a : page.getContent()) {
                        if (a != null && a.getFileAssetId() != null) faIds.add(a.getFileAssetId());
                    }
                    if (!faIds.isEmpty()) {
                        for (var ex : fileAssetExtractionsRepository.findAllById(faIds)) {
                            if (ex == null) continue;
                            String t = ex.getExtractedText();
                            if (t != null && !t.isBlank()) {
                                Map<String, Object> ocr = new LinkedHashMap<>();
                                ocr.put("image_id", String.valueOf(ex.getFileAssetId()));
                                ocr.put("ocr_text", t.trim());
                                ocr.put("ocr_confidence", 1.0);
                                ocrs.add(ocr);
                            }
                        }
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return ocrs;
    }

    private String resolveSnapshotId(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) return null;
        try {
            var actions = moderationActionsRepository.findAllByQueueId(q.getId());
            if (actions != null) {
                String best = null;
                LocalDateTime bestAt = null;
                for (var a : actions) {
                    if (a == null || a.getSnapshot() == null) continue;
                    Object id = a.getSnapshot().get("content_snapshot_id");
                    if (id == null) continue;
                    String sid = String.valueOf(id).trim();
                    if (sid.isBlank()) continue;
                    LocalDateTime at = a.getCreatedAt();
                    if (bestAt == null || (at != null && at.isAfter(bestAt))) {
                        bestAt = at;
                        best = sid;
                    }
                }
                if (best != null) return best;
            }
        } catch (Exception ignore) {
        }
        return buildSyntheticSnapshotId(q);
    }

    private Map<String, Object> resolveReportProfileSnapshotFields(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) return null;
        try {
            var actions = moderationActionsRepository.findAllByQueueId(q.getId());
            if (actions == null || actions.isEmpty()) return null;
            com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity best = null;
            LocalDateTime bestAt = null;
            for (var a : actions) {
                if (a == null) continue;
                if (a.getSnapshot() == null) continue;
                if (a.getReason() == null || !a.getReason().equalsIgnoreCase("REPORT_SNAPSHOT")) continue;
                Map<String, Object> snap = a.getSnapshot();
                Map<String, Object> target = asMap(snap.get("target_snapshot"));
                if (target == null || target.isEmpty()) continue;
                LocalDateTime at = a.getCreatedAt();
                if (bestAt == null || (at != null && at.isAfter(bestAt))) {
                    bestAt = at;
                    best = a;
                }
            }
            if (best == null) return null;
            return asMap(best.getSnapshot().get("target_snapshot"));
        } catch (Exception ignore) {
            return null;
        }
    }

    private Map<String, Object> buildReportContext(ReportTargetType targetType, Long targetId, QueueCtx ctx, String reviewStage) {
        if (targetType == null || targetId == null) return null;
        if (reviewStage == null || !reviewStage.equalsIgnoreCase("reported")) return null;

        int windowMinutes = 10;
        try {
            Object wm = deepGet(ctx == null ? null : ctx.policyConfig(), "review_trigger.window_minutes");
            if (wm instanceof Number n) windowMinutes = Math.max(1, Math.min(60 * 24, n.intValue()));
            else if (wm != null) windowMinutes = Math.max(1, Math.min(60 * 24, Integer.parseInt(String.valueOf(wm).trim())));
        } catch (Exception ignore) {
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0,
                200,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("id"))
        );
        var page = reportsRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
        if (page.getContent().isEmpty()) return null;

        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(windowMinutes);
        LinkedHashSet<Long> uniqueReporters = new LinkedHashSet<>();
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        int total = 0;
        Long latestReportId = null;
        LocalDateTime latestAt = null;

        List<Double> trustScores = new ArrayList<>();
        Map<Long, Double> reporterTrustCache = new HashMap<>();

        for (var r : page.getContent()) {
            if (r == null) continue;
            if (r.getStatus() != ReportStatus.PENDING && r.getStatus() != ReportStatus.REVIEWING) continue;
            LocalDateTime at = r.getCreatedAt();
            if (at == null || at.isBefore(windowStart)) continue;
            total += 1;
            uniqueReporters.add(r.getReporterId());

            if (!reporterTrustCache.containsKey(r.getReporterId())) {
                double ts = 0.5;
                try {
                    var u = usersRepository.findById(r.getReporterId()).orElse(null);
                    if (u != null && u.getMetadata() != null) {
                        Object t = deepGet(u.getMetadata(), "trust_score");
                        if (t instanceof Number n) ts = clamp01(n.doubleValue());
                    }
                } catch (Exception ignore) {
                }
                reporterTrustCache.put(r.getReporterId(), ts);
            }
            trustScores.add(reporterTrustCache.get(r.getReporterId()));

            String code = r.getReasonCode() == null ? null : r.getReasonCode().trim();
            if (code != null && !code.isBlank()) reasons.add(code);
            if (latestAt == null || at.isAfter(latestAt)) {
                latestAt = at;
                latestReportId = r.getId();
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        if (latestReportId != null) out.put("report_id", latestReportId);
        if (latestAt != null) out.put("reported_at", latestAt.toString());
        if (!reasons.isEmpty()) out.put("report_reasons", new ArrayList<>(reasons));
        out.put("report_count_total", total);
        out.put("report_count_unique", uniqueReporters.size());
        out.put("report_velocity", uniqueReporters.size());

        if (!trustScores.isEmpty()) {
            double sum = 0;
            for (Double d : trustScores) sum += d;
            out.put("reporter_trust_agg", sum / trustScores.size());
        }

        String snapshotId = resolveSnapshotId(ctx == null ? null : ctx.queue());
        if (snapshotId != null) out.put("content_snapshot_id", snapshotId);
        return out;
    }

    private String buildPostFilesBlock(Long postId) {
        if (postId == null) return null;
        try {
            var page = postAttachmentsRepository.findByPostId(
                    postId,
                    PageRequest.of(0, 50, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
            );
            if (page.getContent().isEmpty()) return null;
            LinkedHashMap<Long, String> fileNameById = new LinkedHashMap<>();
            for (var a : page.getContent()) {
                if (a == null || a.getFileAssetId() == null) continue;
                String name = a.getFileAsset() != null ? a.getFileAsset().getOriginalName() : null;
                fileNameById.putIfAbsent(a.getFileAssetId(), name);
            }
            if (fileNameById.isEmpty()) return null;
            Map<Long, com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity> exById = new HashMap<>();
            try {
                for (var ex : fileAssetExtractionsRepository.findAllById(fileNameById.keySet())) {
                    if (ex != null && ex.getFileAssetId() != null) exById.put(ex.getFileAssetId(), ex);
                }
            } catch (Exception ignore) {
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[FILES]\n");
            int used = 0;
            for (var entry : fileNameById.entrySet()) {
                Long faId = entry.getKey();
                String name = entry.getValue();
                var ex = exById.get(faId);
                if (ex == null) continue;
                if (ex.getExtractStatus() == null) continue;
                String status = ex.getExtractStatus().name();
                String t = ex.getExtractedText();
                if (t == null) t = "";
                t = t.trim();
                if (t.isBlank()) continue;
                int take = Math.min(2000, t.length());
                String snippet = t.substring(0, take);
                sb.append("- fileAssetId=").append(faId);
                if (name != null && !name.isBlank()) sb.append(" name=").append(name.trim());
                sb.append(" status=").append(status).append('\n');
                sb.append(snippet).append('\n');
                List<ImageRef> derived = imageSupport.tryExtractDerivedImages(faId, ex.getExtractedMetadataJson(), 10);
                if (derived != null && !derived.isEmpty()) {
                    sb.append("images:\n");
                    int it = 0;
                    for (ImageRef img : derived) {
                        if (img == null) continue;
                        String u = blankToNull(img.url());
                        if (u == null) continue;
                        sb.append("  - ").append(u).append('\n');
                        it += 1;
                        if (it >= 5) break;
                    }
                }
                sb.append('\n');
                used += snippet.length();
                if (used >= 4000) break;
            }
            String out = sb.toString().trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object deepGet(Map<String, Object> root, String path) {
        if (root == null || root.isEmpty() || path == null || path.isBlank()) return null;
        String[] segs = path.split("\\.");
        Object cur = root;
        for (String seg : segs) {
            if (seg == null || seg.isBlank()) continue;
            Map<String, Object> m = asMap(cur);
            if (m == null) return null;
            cur = m.get(seg);
        }
        return cur;
    }

    private static Map<String, Object> asMap(Object v) {
        if (!(v instanceof Map<?, ?> mm)) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : mm.entrySet()) {
            Object k = e.getKey();
            if (k == null) continue;
            out.put(String.valueOf(k), e.getValue());
        }
        return out;
    }

    private static List<String> asStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object it : list) {
                if (it == null) continue;
                String s = String.valueOf(it).trim();
                if (!s.isBlank()) out.add(s);
            }
            return out;
        }
        String s = String.valueOf(v).trim();
        return s.isBlank() ? List.of() : List.of(s);
    }

    private static String safeString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static double clamp01(Double v) {
        if (v == null || !Double.isFinite(v)) return 0.5;
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    private static String blankToNull(String s) {
        return AdminModerationLlmConfigSupport.blankToNull(s);
    }

    private static String blankToNull(Object s) {
        if (s == null) return null;
        String t = String.valueOf(s).trim();
        return t.isEmpty() ? null : t;
    }

    private static JsonNode parseJson(ObjectMapper om, String s) {
        try {
            return om.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildPostWebBlock(Long postId, String postContent) {
        if (postId == null) return null;
        StringBuilder scan = new StringBuilder();
        if (postContent != null && !postContent.isBlank()) {
            scan.append(postContent).append('\n');
        }

        int budget = 500_000;
        try {
            var page = postAttachmentsRepository.findByPostId(
                    postId,
                    PageRequest.of(0, 50, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
            );
            if (page != null) {
                page.getContent();
                if (!page.getContent().isEmpty()) {
                    LinkedHashSet<Long> ids = new LinkedHashSet<>();
                    for (var a : page.getContent()) {
                        if (a == null || a.getFileAssetId() == null) continue;
                        ids.add(a.getFileAssetId());
                    }
                    if (!ids.isEmpty()) {
                        for (var ex : fileAssetExtractionsRepository.findAllById(ids)) {
                            if (ex == null) continue;
                            String t = ex.getExtractedText();
                            if (t == null || t.isBlank()) continue;
                            String trimmed = t.trim();
                            int take = Math.clamp(budget, 0, trimmed.length());
                            if (take <= 0) break;
                            scan.append(trimmed, 0, take).append('\n');
                            budget -= take;
                            if (budget <= 0) break;
                        }
                    }
                }
            }
        } catch (Exception ignore) {
        }

        List<String> urls = webContentFetchService.extractUrls(scan.toString());
        if (urls == null || urls.isEmpty()) return null;
        Map<String, Object> meta = webContentFetchService.fetchUrlsToMeta(urls);
        return webContentFetchService.buildWebBlock(meta);
    }
}
