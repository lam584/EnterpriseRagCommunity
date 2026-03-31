package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PortalSearchHitDTO;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
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
    private final RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService;
    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final FileAssetsRepository fileAssetsRepository;
    private final BoardAccessControlService boardAccessControlService;

    private static boolean containsAnyTerm(List<String> queryTerms, String... texts) {
        if (queryTerms == null || queryTerms.isEmpty()) return false;
        if (texts == null || texts.length == 0) return false;
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            if (t == null || t.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(t.toLowerCase(Locale.ROOT));
        }
        if (sb.isEmpty()) return false;
        String haystack = sb.toString();
        for (String term : queryTerms) {
            if (term == null || term.isBlank()) continue;
            if (haystack.contains(term)) return true;
        }
        return false;
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

    private static String normalizeHighlighted(String highlighted) {
        if (highlighted == null || highlighted.isBlank()) return null;
        return highlighted.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private static List<String> buildQueryTerms(String queryText) {
        String q = queryText == null ? "" : queryText.trim().toLowerCase(Locale.ROOT);
        if (q.isBlank()) return List.of();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(q);
        String[] parts = q.split("[\\s,，。！？!?:：;；、/\\\\|]+");
        for (String part : parts) {
            if (part == null) continue;
            String t = part.trim();
            if (t.length() >= 2) terms.add(t);
        }
        return new ArrayList<>(terms);
    }

    private static boolean isRelevantByTextOrScore(List<String> queryTerms, double maxScore, Double score, String... texts) {
        if (containsAnyTerm(queryTerms, texts)) return true;
        double s = safeDouble(score);
        if (maxScore <= 0.0) return s > 0.0;
        double ratio = s / maxScore;
        return ratio >= 0.92;
    }

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

        List<String> queryTerms = buildQueryTerms(q);
        int commentTopK = Math.clamp(fetchLimit, 50, 200);
        List<RagCommentChatRetrievalService.Hit> commentHits = ragCommentChatRetrievalService.retrieve(q, commentTopK);

        LinkedHashMap<Long, RagCommentChatRetrievalService.Hit> bestCommentHitById = new LinkedHashMap<>();
        for (RagCommentChatRetrievalService.Hit h : commentHits) {
            if (h == null || h.getCommentId() == null || h.getPostId() == null) continue;
            bestCommentHitById.putIfAbsent(h.getCommentId(), h);
            if (bestCommentHitById.size() >= fetchLimit) break;
        }

        Set<Long> postIds = new LinkedHashSet<>(bestPostHitById.keySet());
        for (RagCommentChatRetrievalService.Hit h : bestCommentHitById.values()) {
            if (h == null || h.getPostId() == null) continue;
            postIds.add(h.getPostId());
        }

        int fileTopK = Math.clamp(fetchLimit, 50, 200);
        List<RagFileAssetChatRetrievalService.Hit> fileHits = ragFileAssetChatRetrievalService.retrieve(q, fileTopK);
        LinkedHashMap<Long, RagFileAssetChatRetrievalService.Hit> bestFileHitById = new LinkedHashMap<>();
        for (RagFileAssetChatRetrievalService.Hit h : fileHits) {
            if (h == null || h.getFileAssetId() == null) continue;
            bestFileHitById.putIfAbsent(h.getFileAssetId(), h);
            if (bestFileHitById.size() >= fetchLimit) break;
        }

        Set<Long> fileAssetIds = new LinkedHashSet<>(bestFileHitById.keySet());
        for (RagFileAssetChatRetrievalService.Hit h : bestFileHitById.values()) {
            if (h == null || h.getPostIds() == null) continue;
            for (Long pid : h.getPostIds()) {
                if (pid != null) postIds.add(pid);
            }
        }

        Map<Long, PostsEntity> postById = new HashMap<>();
        if (!postIds.isEmpty()) {
            postsRepository.findByIdInAndIsDeletedFalseAndStatus(new ArrayList<>(postIds), PostStatus.PUBLISHED)
                    .forEach(p -> {
                        if (p != null && p.getId() != null) postById.put(p.getId(), p);
                    });
        }
        Set<Long> roleIds = boardAccessControlService.currentUserRoleIds();
        postById.entrySet().removeIf(e -> {
            PostsEntity p = e.getValue();
            if (p == null) return true;
            if (boardId != null && !Objects.equals(boardId, p.getBoardId())) return true;
            Long bid = p.getBoardId();
            return bid != null && !boardAccessControlService.canViewBoard(bid, roleIds);
        });

        Map<Long, CommentsEntity> commentById = new HashMap<>();
        if (!bestCommentHitById.isEmpty()) {
            List<Long> commentIds = new ArrayList<>(bestCommentHitById.keySet());
            for (CommentsEntity c : commentsRepository.findByIdInAndIsDeletedFalseAndStatus(commentIds, CommentStatus.VISIBLE)) {
                if (c != null && c.getId() != null) commentById.put(c.getId(), c);
            }
        }

        Map<Long, FileAssetsEntity> fileAssetById = new HashMap<>();
        if (!fileAssetIds.isEmpty()) {
            for (FileAssetsEntity fa : fileAssetsRepository.findAllById(new ArrayList<>(fileAssetIds))) {
                if (fa != null && fa.getId() != null) fileAssetById.put(fa.getId(), fa);
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
            String highlightedSnippet = normalizeHighlighted(h == null ? null : h.getContentHighlight());
            String highlightedTitle = normalizeHighlighted(h == null ? null : h.getTitleHighlight());

            PortalSearchHitDTO dto = new PortalSearchHitDTO();
            dto.setType("POST");
            dto.setPostId(postId);
            dto.setTitle(p.getTitle());
            dto.setSnippet(snippet);
            dto.setHighlightedTitle(highlightedTitle);
            dto.setHighlightedSnippet(highlightedSnippet);
            dto.setCreatedAt(createdAt);
            dto.setUrl("/portal/posts/detail/" + postId);
            scored.add(new ScoredHit(dto, sim, createdAt, now));
        }

        double maxCommentScore = findMaxScore(bestCommentHitById.values().stream().map(x -> x == null ? null : x.getScore()).toList());
        for (Map.Entry<Long, RagCommentChatRetrievalService.Hit> e : bestCommentHitById.entrySet()) {
            Long commentId = e.getKey();
            CommentsEntity c = commentById.get(commentId);
            if (c == null) continue;
            PostsEntity p = postById.get(c.getPostId());
            if (p == null) continue;
            RagCommentChatRetrievalService.Hit h = e.getValue();
            if (!isRelevantByTextOrScore(queryTerms, maxCommentScore, h == null ? null : h.getScore(), h == null ? null : h.getContentText(), c.getContent())) continue;

            double sim = safeDouble(h == null ? null : h.getScore());
            LocalDateTime createdAt = c.getCreatedAt();
            String snippet = pickSnippet(h == null ? null : h.getContentText(), c.getContent(), 180);
            String highlightedSnippet = normalizeHighlighted(h == null ? null : h.getContentHighlight());

            PortalSearchHitDTO dto = new PortalSearchHitDTO();
            dto.setType("COMMENT");
            dto.setPostId(c.getPostId());
            dto.setCommentId(commentId);
            dto.setTitle(p.getTitle());
            dto.setSnippet(snippet);
            dto.setHighlightedSnippet(highlightedSnippet);
            dto.setCreatedAt(createdAt);
            dto.setUrl("/portal/posts/detail/" + c.getPostId() + "?commentId=" + commentId + "#comment-" + commentId);
            scored.add(new ScoredHit(dto, sim, createdAt, now));
        }

        double maxFileScore = findMaxScore(bestFileHitById.values().stream().map(x -> x == null ? null : x.getScore()).toList());
        for (Map.Entry<Long, RagFileAssetChatRetrievalService.Hit> e : bestFileHitById.entrySet()) {
            Long fileAssetId = e.getKey();
            FileAssetsEntity fa = fileAssetById.get(fileAssetId);
            if (fa == null) continue;

            RagFileAssetChatRetrievalService.Hit h = e.getValue();
            double sim = safeDouble(h == null ? null : h.getScore());
            LocalDateTime createdAt = fa.getCreatedAt();
            String snippet = pickSnippet(h == null ? null : h.getContentText(), null, 180);
            String highlightedSnippet = normalizeHighlighted(h == null ? null : h.getContentHighlight());

            String title = fa.getOriginalName();
            if (title == null || title.isBlank()) title = h == null ? null : h.getFileName();
            if (title == null || title.isBlank()) title = "文件";
            if (!isRelevantByTextOrScore(queryTerms, maxFileScore, h == null ? null : h.getScore(), title, h == null ? null : h.getFileName(), h == null ? null : h.getContentText())) continue;

            Long postId = null;
            if (h != null && h.getPostIds() != null && !h.getPostIds().isEmpty()) {
                for (Long pid : h.getPostIds()) {
                    if (pid != null && postById.containsKey(pid)) {
                        postId = pid;
                        break;
                    }
                }
            }
            String url = postId == null ? null : ("/portal/posts/detail/" + postId);

            PortalSearchHitDTO dto = new PortalSearchHitDTO();
            dto.setType("FILE");
            dto.setFileAssetId(fileAssetId);
            dto.setPostId(postId);
            dto.setTitle(title);
            dto.setSnippet(snippet);
            dto.setHighlightedSnippet(highlightedSnippet);
            dto.setCreatedAt(createdAt);
            dto.setUrl(url);
            scored.add(new ScoredHit(dto, sim, createdAt, now));
        }

        applyRanking(scored);

        scored.sort((a, b) -> {
            int d = Double.compare(b.finalScore, a.finalScore);
            if (d != 0) return d;
            LocalDateTime ta = a.createdAt;
            LocalDateTime tb = b.createdAt;
            if (ta != null && tb != null) {
                return tb.compareTo(ta);
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

    private static double findMaxScore(List<Double> scores) {
        double max = 0.0;
        if (scores == null || scores.isEmpty()) return max;
        for (Double x : scores) {
            double v = safeDouble(x);
            if (v > max) max = v;
        }
        return max;
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
