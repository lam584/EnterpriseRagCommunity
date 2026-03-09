package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

@Service
@RequiredArgsConstructor
public class RagChatPostCommentAggregationService {

    private final PostsRepository postsRepository;

    public List<RagPostChatRetrievalService.Hit> aggregate(
            String queryText,
            List<RagPostChatRetrievalService.Hit> postHits,
            List<RagCommentChatRetrievalService.Hit> commentHits,
            Config cfg
    ) {
        Config safe = cfg == null ? new Config() : cfg;
        int maxPosts = safe.getMaxPosts() == null ? 6 : clampInt(safe.getMaxPosts(), 1, 100, 6);
        int perPostMaxCommentChunks = safe.getPerPostMaxCommentChunks() == null ? 2 : clampInt(safe.getPerPostMaxCommentChunks(), 0, 50, 2);
        int postContentMaxTokens = safe.getPostContentMaxTokens() == null ? 1200 : clampInt(safe.getPostContentMaxTokens(), 50, 200_000, 1200);
        int commentChunkMaxTokens = safe.getCommentChunkMaxTokens() == null ? 400 : clampInt(safe.getCommentChunkMaxTokens(), 20, 200_000, 400);

        IncludePostContentPolicy postPolicy = safe.getIncludePostContentPolicy() == null
                ? IncludePostContentPolicy.ON_COMMENT_HIT
                : safe.getIncludePostContentPolicy();

        Map<Long, RagPostChatRetrievalService.Hit> bestPostHitById = new LinkedHashMap<>();
        if (postHits != null) {
            for (RagPostChatRetrievalService.Hit h : postHits) {
                if (h == null || h.getPostId() == null) continue;
                bestPostHitById.putIfAbsent(h.getPostId(), h);
            }
        }

        Map<Long, List<RagCommentChatRetrievalService.Hit>> commentsByPostId = new HashMap<>();
        if (commentHits != null) {
            for (RagCommentChatRetrievalService.Hit h : commentHits) {
                if (h == null || h.getPostId() == null) continue;
                commentsByPostId.computeIfAbsent(h.getPostId(), _k -> new ArrayList<>()).add(h);
            }
        }
        for (List<RagCommentChatRetrievalService.Hit> lst : commentsByPostId.values()) {
            lst.sort(Comparator.comparing((RagCommentChatRetrievalService.Hit h) -> h.getScore() == null ? 0.0 : h.getScore()).reversed());
        }

        Set<Long> candidatePostIds = new LinkedHashSet<>();
        candidatePostIds.addAll(bestPostHitById.keySet());
        candidatePostIds.addAll(commentsByPostId.keySet());

        Map<Long, PostsEntity> postEntityById = fetchPosts(candidatePostIds);

        List<PostAgg> aggs = new ArrayList<>();
        for (Long postId : candidatePostIds) {
            if (postId == null) continue;
            RagPostChatRetrievalService.Hit bestPostHit = bestPostHitById.get(postId);
            List<RagCommentChatRetrievalService.Hit> comms = commentsByPostId.get(postId);
            boolean hasComment = comms != null && !comms.isEmpty();
            boolean hasFileAssetContent = bestPostHit != null
                    && (bestPostHit.getFileAssetId() != null
                    || "FILE_ASSET".equalsIgnoreCase(trimOrNull(bestPostHit.getSourceType())));
            boolean needPostContent = switch (postPolicy) {
                case ALWAYS -> true;
                case NEVER -> hasFileAssetContent;
                default -> hasComment || hasFileAssetContent;
            };

            PostsEntity pe = postEntityById.get(postId);
            String title = bestPostHit != null && bestPostHit.getTitle() != null && !bestPostHit.getTitle().isBlank()
                    ? bestPostHit.getTitle().trim()
                    : (pe == null ? null : trimOrNull(pe.getTitle()));

            String postText = null;
            if (needPostContent) {
                if (bestPostHit != null && bestPostHit.getContentText() != null && !bestPostHit.getContentText().isBlank()) {
                    postText = truncateByApproxTokens(bestPostHit.getContentText().trim(), postContentMaxTokens);
                } else if (pe != null && pe.getContent() != null && !pe.getContent().isBlank()) {
                    postText = truncateByApproxTokens(pe.getContent().trim(), postContentMaxTokens);
                }
            }

            String commentText = null;
            if (hasComment && perPostMaxCommentChunks > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append("命中评论片段：\n");
                int kept = 0;
                Set<Long> seen = new java.util.HashSet<>();
                for (RagCommentChatRetrievalService.Hit ch : comms) {
                    if (ch == null) continue;
                    if (kept >= perPostMaxCommentChunks) break;
                    String raw = ch.getContentText();
                    if (raw == null || raw.isBlank()) continue;
                    String trimmed = raw.trim();
                    long h32 = crc32(truncatedHashBasis(trimmed, commentChunkMaxTokens));
                    if (seen.contains(h32)) continue;
                    seen.add(h32);

                    String part = truncateByApproxTokens(trimmed, commentChunkMaxTokens);
                    sb.append("- ");
                    if (ch.getCommentId() != null) sb.append("comment_id=").append(ch.getCommentId()).append(' ');
                    if (ch.getChunkIndex() != null) sb.append("chunk=").append(ch.getChunkIndex()).append(' ');
                    if (ch.getScore() != null) sb.append("score=").append(String.format(Locale.ROOT, "%.4f", ch.getScore())).append(' ');
                    sb.append('\n');
                    sb.append(part).append('\n');
                    kept++;
                }
                commentText = sb.toString().trim();
            }

            StringBuilder combined = new StringBuilder();
            if (postText != null && !postText.isBlank()) {
                combined.append("帖子正文：\n").append(postText.trim()).append('\n');
            }
            if (commentText != null && !commentText.isBlank()) {
                if (!combined.isEmpty()) combined.append('\n');
                combined.append(commentText.trim()).append('\n');
            }

            String combinedText = combined.toString().trim();
            if (combinedText.isBlank()) continue;

            double score = 0.0;
            if (bestPostHit != null && bestPostHit.getScore() != null) score = Math.max(score, bestPostHit.getScore());
            if (hasComment && comms.get(0) != null && comms.get(0).getScore() != null) score = Math.max(score, comms.get(0).getScore());

            PostAgg a = new PostAgg();
            a.postId = postId;
            a.title = title;
            a.boardId = bestPostHit != null ? bestPostHit.getBoardId() : (pe == null ? null : pe.getBoardId());
            a.chunkIndex = bestPostHit != null ? bestPostHit.getChunkIndex() : null;
            a.score = score;
            a.contentText = combinedText;
            a.sourceType = bestPostHit == null ? null : bestPostHit.getSourceType();
            a.fileAssetId = bestPostHit == null ? null : bestPostHit.getFileAssetId();
            a.type = bestPostHit == null ? null : bestPostHit.getType();
            aggs.add(a);
        }

        aggs.sort(Comparator.comparing((PostAgg a) -> a.score).reversed());

        List<RagPostChatRetrievalService.Hit> out = new ArrayList<>();
        int limit = Math.min(maxPosts, aggs.size());
        for (int i = 0; i < limit; i++) {
            PostAgg a = aggs.get(i);
            RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
            h.setDocId("agg_post_" + a.postId);
            h.setPostId(a.postId);
            h.setBoardId(a.boardId);
            h.setChunkIndex(a.chunkIndex);
            h.setScore(a.score);
            h.setTitle(a.title);
            h.setContentText(a.contentText);
            h.setSourceType(a.sourceType);
            h.setFileAssetId(a.fileAssetId);
            h.setType(a.type);
            out.add(h);
        }
        return out;
    }

    private Map<Long, PostsEntity> fetchPosts(Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        List<Long> ids = postIds.stream().filter(x -> x != null && x > 0).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, PostsEntity> out = new HashMap<>();
        for (PostsEntity p : postsRepository.findByIdInAndIsDeletedFalseAndStatus(ids, PostStatus.PUBLISHED)) {
            if (p == null || p.getId() == null) continue;
            out.put(p.getId(), p);
        }
        return out;
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static long crc32(String s) {
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    private static String truncatedHashBasis(String s, int maxTokens) {
        return truncateByApproxTokens(s, maxTokens);
    }

    static int approxTokens(String s) {
        if (s == null || s.isEmpty()) return 0;
        double t = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x7f) t += 0.25;
            else t += 1.0;
        }
        return Math.max(0, (int) Math.ceil(t));
    }

    static String truncateByApproxTokens(String s, int maxTokens) {
        if (s == null) return "";
        int cap = Math.max(0, maxTokens);
        if (cap <= 0) return "";
        if (approxTokens(s) <= cap) return s;
        int lo = 0;
        int hi = s.length();
        int best = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            String sub = s.substring(0, mid);
            int tok = approxTokens(sub);
            if (tok <= cap) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, best);
    }

    private static class PostAgg {
        private Long postId;
        private Long boardId;
        private Integer chunkIndex;
        private double score;
        private String title;
        private String contentText;
        private String sourceType;
        private Long fileAssetId;
        private com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType type;
    }

    @Data
    public static class Config {
        private Integer maxPosts;
        private Integer perPostMaxCommentChunks;
        private IncludePostContentPolicy includePostContentPolicy;
        private Integer postContentMaxTokens;
        private Integer commentChunkMaxTokens;
    }

    public enum IncludePostContentPolicy {
        ALWAYS, ON_COMMENT_HIT, NEVER
    }
}
