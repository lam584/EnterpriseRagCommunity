package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PortalSearchHitDTO;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalSearchService {

    private final HybridRagRetrievalService hybridRagRetrievalService;
    private final HybridRetrievalConfigService hybridRetrievalConfigService;
    private final RagCommentChatRetrievalService ragCommentChatRetrievalService;
    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;

    public Page<PortalSearchHitDTO> search(String queryText, Long boardId, int page, int pageSize) {
        String q = queryText == null ? "" : queryText.trim();
        if (q.isBlank()) {
            return new PageImpl<>(List.of(), PageRequest.of(Math.max(0, page - 1), clampPageSize(pageSize)), 0);
        }

        int safePage = Math.max(1, page);
        int safePageSize = clampPageSize(pageSize);
        int fetchLimit = Math.min(500, Math.max(50, (safePage + 1) * safePageSize * 5));

        HybridRetrievalConfigDTO baseCfg = hybridRetrievalConfigService.getConfigOrDefault();
        HybridRetrievalConfigDTO cfg = hybridRetrievalConfigService.normalizeConfig(baseCfg);
        cfg.setBm25K(Math.max(cfg.getBm25K() == null ? 0 : cfg.getBm25K(), fetchLimit));
        cfg.setVecK(Math.max(cfg.getVecK() == null ? 0 : cfg.getVecK(), fetchLimit));
        cfg.setHybridK(Math.max(cfg.getHybridK() == null ? 0 : cfg.getHybridK(), fetchLimit));
        cfg.setMaxDocs(Math.max(cfg.getMaxDocs() == null ? 0 : cfg.getMaxDocs(), fetchLimit));

        HybridRagRetrievalService.RetrieveResult rr = hybridRagRetrievalService.retrieve(q, boardId, cfg, false);
        List<HybridRagRetrievalService.DocHit> postDocHits = rr == null || rr.getFinalHits() == null ? List.of() : rr.getFinalHits();

        LinkedHashMap<Long, HybridRagRetrievalService.DocHit> bestPostHitById = new LinkedHashMap<>();
        for (HybridRagRetrievalService.DocHit h : postDocHits) {
            if (h == null || h.getPostId() == null) continue;
            bestPostHitById.putIfAbsent(h.getPostId(), h);
            if (bestPostHitById.size() >= fetchLimit) break;
        }

        int commentTopK = Math.min(200, Math.max(50, fetchLimit));
        List<RagCommentChatRetrievalService.Hit> commentHits = ragCommentChatRetrievalService.retrieve(q, commentTopK);

        LinkedHashMap<Long, RagCommentChatRetrievalService.Hit> bestCommentHitById = new LinkedHashMap<>();
        for (RagCommentChatRetrievalService.Hit h : commentHits) {
            if (h == null || h.getCommentId() == null || h.getPostId() == null) continue;
            bestCommentHitById.putIfAbsent(h.getCommentId(), h);
            if (bestCommentHitById.size() >= fetchLimit) break;
        }

        Set<Long> postIds = new LinkedHashSet<>();
        postIds.addAll(bestPostHitById.keySet());
        for (RagCommentChatRetrievalService.Hit h : bestCommentHitById.values()) {
            if (h == null || h.getPostId() == null) continue;
            postIds.add(h.getPostId());
        }

        Map<Long, PostsEntity> postById = new HashMap<>();
        if (!postIds.isEmpty()) {
            postsRepository.findByIdInAndIsDeletedFalseAndStatus(new ArrayList<>(postIds), PostStatus.PUBLISHED)
                    .forEach(p -> {
                        if (p != null && p.getId() != null) postById.put(p.getId(), p);
                    });
        }

        Map<Long, CommentsEntity> commentById = new HashMap<>();
        if (!bestCommentHitById.isEmpty()) {
            List<Long> commentIds = new ArrayList<>(bestCommentHitById.keySet());
            for (CommentsEntity c : commentsRepository.findByIdInAndIsDeletedFalseAndStatus(commentIds, CommentStatus.VISIBLE)) {
                if (c != null && c.getId() != null) commentById.put(c.getId(), c);
            }
        }

        LocalDateTime now = LocalDateTime.now();

        List<ScoredHit> scored = new ArrayList<>();
        for (Map.Entry<Long, HybridRagRetrievalService.DocHit> e : bestPostHitById.entrySet()) {
            Long postId = e.getKey();
            PostsEntity p = postById.get(postId);
            if (p == null) continue;
            HybridRagRetrievalService.DocHit h = e.getValue();

            double sim = safeDouble(h == null ? null : (h.getRerankScore() != null ? h.getRerankScore() : (h.getFusedScore() != null ? h.getFusedScore() : (h.getVecScore() != null ? h.getVecScore() : h.getScore()))));
            LocalDateTime createdAt = p.getPublishedAt() != null ? p.getPublishedAt() : p.getCreatedAt();
            String snippet = pickSnippet(h == null ? null : h.getContentText(), p.getContent(), 180);

            PortalSearchHitDTO dto = new PortalSearchHitDTO();
            dto.setType("POST");
            dto.setPostId(postId);
            dto.setTitle(p.getTitle());
            dto.setSnippet(snippet);
            dto.setCreatedAt(createdAt);
            dto.setUrl("/portal/posts/detail/" + postId);
            scored.add(new ScoredHit(dto, sim, createdAt, now));
        }

        for (Map.Entry<Long, RagCommentChatRetrievalService.Hit> e : bestCommentHitById.entrySet()) {
            Long commentId = e.getKey();
            CommentsEntity c = commentById.get(commentId);
            if (c == null) continue;
            PostsEntity p = postById.get(c.getPostId());
            if (p == null) continue;
            RagCommentChatRetrievalService.Hit h = e.getValue();

            double sim = safeDouble(h == null ? null : h.getScore());
            LocalDateTime createdAt = c.getCreatedAt();
            String snippet = pickSnippet(h == null ? null : h.getContentText(), c.getContent(), 180);

            PortalSearchHitDTO dto = new PortalSearchHitDTO();
            dto.setType("COMMENT");
            dto.setPostId(c.getPostId());
            dto.setCommentId(commentId);
            dto.setTitle(p.getTitle());
            dto.setSnippet(snippet);
            dto.setCreatedAt(createdAt);
            dto.setUrl("/portal/posts/detail/" + c.getPostId() + "?commentId=" + commentId + "#comment-" + commentId);
            scored.add(new ScoredHit(dto, sim, createdAt, now));
        }

        applyRanking(scored);

        scored.sort((a, b) -> {
            int d = Double.compare(b.finalScore, a.finalScore);
            if (d != 0) return d;
            LocalDateTime ta = a.createdAt;
            LocalDateTime tb = b.createdAt;
            if (ta != null && tb != null) {
                int dt = tb.compareTo(ta);
                if (dt != 0) return dt;
            } else if (ta == null && tb != null) {
                return 1;
            } else if (ta != null) {
                return -1;
            }
            return 0;
        });

        int offset = (safePage - 1) * safePageSize;
        if (offset >= scored.size()) {
            return new PageImpl<>(List.of(), PageRequest.of(safePage - 1, safePageSize), scored.size());
        }
        int end = Math.min(scored.size(), offset + safePageSize);
        List<PortalSearchHitDTO> content = scored.subList(offset, end).stream().map(x -> x.dto).toList();

        boolean hasMore = scored.size() > end;
        long totalElements = hasMore ? (long) offset + content.size() + 1 : (long) offset + content.size();
        return new PageImpl<>(content, PageRequest.of(safePage - 1, safePageSize), totalElements);
    }

    private static void applyRanking(List<ScoredHit> scored) {
        if (scored == null || scored.isEmpty()) return;

        double postMin = Double.POSITIVE_INFINITY;
        double postMax = Double.NEGATIVE_INFINITY;
        double commentMin = Double.POSITIVE_INFINITY;
        double commentMax = Double.NEGATIVE_INFINITY;

        for (ScoredHit h : scored) {
            if (h == null) continue;
            if ("COMMENT".equalsIgnoreCase(h.dto.getType())) {
                commentMin = Math.min(commentMin, h.sim);
                commentMax = Math.max(commentMax, h.sim);
            } else {
                postMin = Math.min(postMin, h.sim);
                postMax = Math.max(postMax, h.sim);
            }
        }

        for (ScoredHit h : scored) {
            if (h == null) continue;
            boolean isComment = "COMMENT".equalsIgnoreCase(h.dto.getType());
            double min = isComment ? commentMin : postMin;
            double max = isComment ? commentMax : postMax;
            double normSim = normalizeMinMax(h.sim, min, max);

            double recency = h.recency;
            double wSim = isComment ? 0.9 : 0.85;
            double wTime = isComment ? 0.1 : 0.15;
            h.finalScore = wSim * normSim + wTime * recency;
            h.dto.setScore(h.finalScore);
        }
    }

    private static double normalizeMinMax(double v, double min, double max) {
        if (Double.isInfinite(min) || Double.isInfinite(max)) return 0.0;
        if (max <= min) return 1.0;
        double x = (v - min) / (max - min);
        if (x < 0) x = 0;
        if (x > 1) x = 1;
        return x;
    }

    private static int clampPageSize(int pageSize) {
        int ps = pageSize <= 0 ? 20 : pageSize;
        return Math.min(ps, 50);
    }

    private static double safeDouble(Double v) {
        if (v == null) return 0.0;
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return v;
    }

    private static String pickSnippet(String preferred, String fallback, int maxChars) {
        String s = (preferred == null || preferred.isBlank()) ? (fallback == null ? "" : fallback) : preferred;
        s = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "...";
    }

    private static class ScoredHit {
        final PortalSearchHitDTO dto;
        final double sim;
        final LocalDateTime createdAt;
        final double recency;
        double finalScore;

        ScoredHit(PortalSearchHitDTO dto, double sim, LocalDateTime createdAt, LocalDateTime now) {
            this.dto = dto;
            this.sim = sim;
            this.createdAt = createdAt;
            this.recency = computeRecency(createdAt, now);
            this.finalScore = sim;
        }
    }

    private static double computeRecency(LocalDateTime t, LocalDateTime now) {
        if (t == null || now == null) return 0.0;
        long days = Math.max(0, Duration.between(t, now).toDays());
        double halfLifeDays = 30.0;
        double lambda = Math.log(2.0) / halfLifeDays;
        return Math.exp(-lambda * days);
    }
}
