package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final CommentsRepository commentsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;

    private static List<RagPostChatRetrievalService.Hit> fallbackToPostHits(
            List<RagPostChatRetrievalService.Hit> postHits,
            int maxPosts
    ) {
        if (postHits == null || postHits.isEmpty() || maxPosts <= 0) return List.of();
        List<RagPostChatRetrievalService.Hit> out = new ArrayList<>();
        Set<Long> seenPostIds = new LinkedHashSet<>();
        for (RagPostChatRetrievalService.Hit h : postHits) {
            if (h == null || h.getPostId() == null) continue;
            if (!seenPostIds.add(h.getPostId())) continue;
            String text = h.getContentText();
            if (text == null || text.isBlank()) continue;
            out.add(h);
            if (out.size() >= maxPosts) break;
        }
        return out;
    }

    private static boolean isAttachmentHit(RagPostChatRetrievalService.Hit h) {
        if (h == null) return false;
        if (h.getFileAssetId() != null) return true;
        String st = trimOrNull(h.getSourceType());
        return "FILE_ASSET".equalsIgnoreCase(st);
    }

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
        int perPostMaxAttachmentChunks = Math.clamp(perPostMaxCommentChunks <= 0 ? 2 : perPostMaxCommentChunks, 1, 3);
        int attachmentChunkMaxTokens = Math.clamp(commentChunkMaxTokens, 80, 300);

        IncludePostContentPolicy postPolicy = safe.getIncludePostContentPolicy() == null
                ? IncludePostContentPolicy.ON_COMMENT_HIT
                : safe.getIncludePostContentPolicy();

        Map<Long, RagPostChatRetrievalService.Hit> bestPostHitById = new LinkedHashMap<>();
        Map<Long, List<RagPostChatRetrievalService.Hit>> attachmentHitsByPostId = new HashMap<>();
        if (postHits != null) {
            for (RagPostChatRetrievalService.Hit h : postHits) {
                if (h == null || h.getPostId() == null) continue;
                bestPostHitById.putIfAbsent(h.getPostId(), h);
                if (isAttachmentHit(h)) {
                    attachmentHitsByPostId.computeIfAbsent(h.getPostId(), _k -> new ArrayList<>()).add(h);
                }
            }
        }
        for (List<RagPostChatRetrievalService.Hit> lst : attachmentHitsByPostId.values()) {
            lst.sort(Comparator.comparing((RagPostChatRetrievalService.Hit h) -> h.getScore() == null ? 0.0 : h.getScore()).reversed());
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
        candidatePostIds.addAll(attachmentHitsByPostId.keySet());

        Map<Long, PostsEntity> postEntityById = fetchPosts(candidatePostIds);

        List<PostAgg> aggs = new ArrayList<>();
        for (Long postId : candidatePostIds) {
            if (postId == null) continue;
            RagPostChatRetrievalService.Hit bestPostHit = bestPostHitById.get(postId);
            List<RagCommentChatRetrievalService.Hit> comms = commentsByPostId.get(postId);
            List<RagPostChatRetrievalService.Hit> fileHits = attachmentHitsByPostId.get(postId);
            boolean hasComment = comms != null && !comms.isEmpty();
            boolean hasAttachmentHit = fileHits != null && !fileHits.isEmpty();
            boolean hasPostHit = bestPostHit != null;
            boolean hasFileAssetContent = bestPostHit != null
                    && (bestPostHit.getFileAssetId() != null
                    || "FILE_ASSET".equalsIgnoreCase(trimOrNull(bestPostHit.getSourceType())));
            boolean needPostContent = switch (postPolicy) {
                case ALWAYS -> true;
                case NEVER -> hasFileAssetContent;
                // ON_COMMENT_HIT keeps prior behavior for comment/file hits,
                // and also preserves direct post hits to avoid dropping post-only recall.
                default -> hasComment || hasFileAssetContent || hasPostHit || hasAttachmentHit;
            };

            PostsEntity pe = postEntityById.get(postId);
            String title = bestPostHit != null && bestPostHit.getTitle() != null && !bestPostHit.getTitle().isBlank()
                    ? bestPostHit.getTitle().trim()
                    : (pe == null ? null : trimOrNull(pe.getTitle()));

            String postText = null;
            if (needPostContent) {
                boolean bestIsAttachment = isAttachmentHit(bestPostHit);
                if (!bestIsAttachment && bestPostHit != null && bestPostHit.getContentText() != null && !bestPostHit.getContentText().isBlank()) {
                    postText = truncateByApproxTokens(bestPostHit.getContentText().trim(), postContentMaxTokens);
                } else if (pe != null && pe.getContent() != null && !pe.getContent().isBlank()) {
                    postText = truncateByApproxTokens(pe.getContent().trim(), postContentMaxTokens);
                }
            }

            CommentContext commentCtx = buildCommentContext(postId, comms, perPostMaxCommentChunks, commentChunkMaxTokens);
            String commentText = commentCtx == null ? null : commentCtx.text;
            String attachmentText = buildAttachmentContext(postId, fileHits, perPostMaxAttachmentChunks, attachmentChunkMaxTokens);

            StringBuilder combined = new StringBuilder();
            if (postText != null && !postText.isBlank()) {
                combined.append("帖子正文：\n").append(postText.trim()).append('\n');
            }
            if (commentText != null && !commentText.isBlank()) {
                if (!combined.isEmpty()) combined.append('\n');
                combined.append(commentText.trim()).append('\n');
            }
            if (attachmentText != null && !attachmentText.isBlank()) {
                if (!combined.isEmpty()) combined.append('\n');
                combined.append(attachmentText.trim()).append('\n');
            }

            String combinedText = combined.toString().trim();
            if (combinedText.isBlank()) continue;

            double score = 0.0;
            if (bestPostHit != null && bestPostHit.getScore() != null) score = Math.max(score, bestPostHit.getScore());
            if (hasComment && comms.get(0) != null && comms.get(0).getScore() != null) score = Math.max(score, comms.get(0).getScore());
            if (hasAttachmentHit && fileHits.get(0) != null && fileHits.get(0).getScore() != null)
                score = Math.max(score, fileHits.get(0).getScore());

            PostAgg a = new PostAgg();
            a.postId = postId;
            a.title = title;
            a.boardId = bestPostHit != null ? bestPostHit.getBoardId() : (pe == null ? null : pe.getBoardId());
            a.commentId = commentCtx == null ? null : commentCtx.primaryCommentId;
            a.chunkIndex = commentCtx != null && commentCtx.primaryChunkIndex != null
                    ? commentCtx.primaryChunkIndex
                    : (bestPostHit != null ? bestPostHit.getChunkIndex() : null);
            a.score = score;
            a.contentText = combinedText;
            a.sourceType = bestPostHit == null
                    ? (a.commentId == null ? null : "COMMENT")
                    : bestPostHit.getSourceType();
            a.fileAssetId = bestPostHit == null ? null : bestPostHit.getFileAssetId();
            a.type = bestPostHit == null ? null : bestPostHit.getType();
            aggs.add(a);
        }

        aggs.sort(Comparator.comparing((PostAgg a) -> a.score).reversed());

        List<RagPostChatRetrievalService.Hit> out = new ArrayList<>();
        int limit = Math.min(maxPosts, aggs.size());
        for (int i = 0; i < limit; i++) {
            out.add(toAggregatedHit(aggs.get(i)));
        }
        if (out.isEmpty()) {
            List<RagPostChatRetrievalService.Hit> fallback = fallbackToPostHits(postHits, maxPosts);
            if (!fallback.isEmpty()) return fallback;
        }
        return out;
    }

    private CommentContext buildCommentContext(
            Long postId,
            List<RagCommentChatRetrievalService.Hit> hitComments,
            int maxChunks,
            int chunkMaxTokens
    ) {
        if (postId == null || maxChunks <= 0) return null;
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        Set<Long> seenHashes = new java.util.HashSet<>();
        Set<Long> seenCommentIds = new java.util.HashSet<>();
        Long primaryCommentId = null;
        Integer primaryChunkIndex = null;

        if (hitComments != null) {
            for (RagCommentChatRetrievalService.Hit ch : hitComments) {
                if (ch == null) continue;
                if (kept >= maxChunks) break;
                String raw = ch.getContentText();
                if (raw == null || raw.isBlank()) continue;
                String trimmed = raw.trim();
                long h32 = crc32(truncatedHashBasis(trimmed, chunkMaxTokens));
                if (seenHashes.contains(h32)) continue;
                seenHashes.add(h32);
                if (ch.getCommentId() != null) seenCommentIds.add(ch.getCommentId());
                if (primaryCommentId == null && ch.getCommentId() != null) {
                    primaryCommentId = ch.getCommentId();
                    primaryChunkIndex = ch.getChunkIndex();
                }

                String part = truncateByApproxTokens(trimmed, chunkMaxTokens);
                if (sb.isEmpty()) sb.append("命中评论片段：\n");
                sb.append("- ");
                if (ch.getCommentId() != null) sb.append("comment_id=").append(ch.getCommentId()).append(' ');
                if (ch.getChunkIndex() != null) sb.append("chunk=").append(ch.getChunkIndex()).append(' ');
                if (ch.getScore() != null)
                    sb.append("score=").append(String.format(Locale.ROOT, "%.4f", ch.getScore())).append(' ');
                sb.append("source=hit").append('\n');
                sb.append(part).append('\n');
                kept++;
            }
        }

        if (kept < maxChunks) {
            List<CommentsEntity> dbComments = fetchVisibleComments(postId, Math.max(maxChunks * 2, 10));
            for (CommentsEntity c : dbComments) {
                if (c == null) continue;
                if (kept >= maxChunks) break;
                if (c.getId() != null && seenCommentIds.contains(c.getId())) continue;
                String raw = c.getContent();
                if (raw == null || raw.isBlank()) continue;
                String trimmed = raw.trim();
                long h32 = crc32(truncatedHashBasis(trimmed, chunkMaxTokens));
                if (seenHashes.contains(h32)) continue;
                seenHashes.add(h32);
                if (c.getId() != null) seenCommentIds.add(c.getId());
                if (primaryCommentId == null && c.getId() != null) {
                    primaryCommentId = c.getId();
                }

                String part = truncateByApproxTokens(trimmed, chunkMaxTokens);
                if (sb.isEmpty()) sb.append("命中评论片段：\n");
                sb.append("- ");
                if (c.getId() != null) sb.append("comment_id=").append(c.getId()).append(' ');
                sb.append("source=db_visible").append('\n');
                sb.append(part).append('\n');
                kept++;
            }
        }

        if (kept <= 0) return null;
        CommentContext out = new CommentContext();
        out.text = sb.toString().trim();
        out.primaryCommentId = primaryCommentId;
        out.primaryChunkIndex = primaryChunkIndex;
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

    private String buildAttachmentContext(
            Long postId,
            List<RagPostChatRetrievalService.Hit> hitAttachments,
            int maxChunks,
            int chunkMaxTokens
    ) {
        if (postId == null || maxChunks <= 0) return null;
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        Set<Long> seenHashes = new java.util.HashSet<>();
        Set<Long> seenFileAssetIds = new java.util.HashSet<>();

        if (hitAttachments != null) {
            for (RagPostChatRetrievalService.Hit h : hitAttachments) {
                if (h == null) continue;
                if (kept >= maxChunks) break;
                String raw = h.getContentText();
                if (raw == null || raw.isBlank()) continue;
                String trimmed = raw.trim();
                long h32 = crc32(truncatedHashBasis(trimmed, chunkMaxTokens));
                if (seenHashes.contains(h32)) continue;
                seenHashes.add(h32);
                if (h.getFileAssetId() != null) seenFileAssetIds.add(h.getFileAssetId());

                String part = truncateByApproxTokens(trimmed, chunkMaxTokens);
                if (sb.isEmpty()) sb.append("关联附件片段：\n");
                sb.append("- ");
                if (h.getFileAssetId() != null) sb.append("file_asset_id=").append(h.getFileAssetId()).append(' ');
                if (h.getChunkIndex() != null) sb.append("chunk=").append(h.getChunkIndex()).append(' ');
                if (h.getScore() != null)
                    sb.append("score=").append(String.format(Locale.ROOT, "%.4f", h.getScore())).append(' ');
                sb.append("source=hit").append('\n');
                sb.append(part).append('\n');
                kept++;
            }
        }

        if (kept < maxChunks) {
            List<PostAttachmentsEntity> atts = new ArrayList<>(fetchPostAttachments(postId));
            if (!atts.isEmpty()) {
                atts.sort(Comparator
                        .comparing(PostAttachmentsEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PostAttachmentsEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));

                List<Long> fileIds = atts.stream()
                        .map(PostAttachmentsEntity::getFileAssetId)
                        .filter(x -> x != null && x > 0)
                        .distinct()
                        .toList();
                Map<Long, FileAssetExtractionsEntity> extractionByFileId = fetchExtractionsByFileIds(fileIds);

                for (PostAttachmentsEntity att : atts) {
                    if (att == null) continue;
                    if (kept >= maxChunks) break;
                    Long fileAssetId = att.getFileAssetId();
                    if (fileAssetId == null || fileAssetId <= 0) continue;
                    if (seenFileAssetIds.contains(fileAssetId)) continue;
                    FileAssetExtractionsEntity ex = extractionByFileId.get(fileAssetId);
                    String raw = ex == null ? null : ex.getExtractedText();
                    if (raw == null || raw.isBlank()) continue;
                    String trimmed = raw.trim();
                    long h32 = crc32(truncatedHashBasis(trimmed, chunkMaxTokens));
                    if (seenHashes.contains(h32)) continue;
                    seenHashes.add(h32);
                    seenFileAssetIds.add(fileAssetId);

                    String part = truncateByApproxTokens(trimmed, chunkMaxTokens);
                    if (sb.isEmpty()) sb.append("关联附件片段：\n");
                    sb.append("- file_asset_id=").append(fileAssetId).append(' ').append("source=db_extraction").append('\n');
                    sb.append(part).append('\n');
                    kept++;
                }
            }
        }

        if (kept <= 0) return null;
        return sb.toString().trim();
    }

    private List<CommentsEntity> fetchVisibleComments(Long postId, int limit) {
        if (postId == null || postId <= 0 || limit <= 0) return List.of();
        try {
            Page<CommentsEntity> page = commentsRepository.findByPostIdAndStatusAndIsDeletedFalse(
                    postId,
                    CommentStatus.VISIBLE,
                    PageRequest.of(0, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
            );
            if (page.getContent().isEmpty()) return List.of();
            return page.getContent();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<PostAttachmentsEntity> fetchPostAttachments(Long postId) {
        if (postId == null || postId <= 0) return List.of();
        try {
            List<PostAttachmentsEntity> rows = postAttachmentsRepository.findByPostId(postId);
            if (rows == null || rows.isEmpty()) return List.of();
            return rows;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<Long, FileAssetExtractionsEntity> fetchExtractionsByFileIds(List<Long> fileAssetIds) {
        if (fileAssetIds == null || fileAssetIds.isEmpty()) return Map.of();
        Map<Long, FileAssetExtractionsEntity> out = new HashMap<>();
        try {
            for (FileAssetExtractionsEntity e : fileAssetExtractionsRepository.findAllById(fileAssetIds)) {
                if (e == null || e.getFileAssetId() == null) continue;
                out.putIfAbsent(e.getFileAssetId(), e);
            }
            return out;
        } catch (Exception ignored) {
            return Map.of();
        }
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
        if (cap == 0) return "";
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
        private Long commentId;
        private Integer chunkIndex;
        private double score;
        private String title;
        private String contentText;
        private String sourceType;
        private Long fileAssetId;
        private com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType type;
    }

    private static RagPostChatRetrievalService.Hit toAggregatedHit(PostAgg a) {
        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setDocId("agg_post_" + a.postId);
        h.setPostId(a.postId);
        h.setBoardId(a.boardId);
        h.setChunkIndex(a.chunkIndex);
        h.setCommentId(a.commentId);
        h.setScore(a.score);
        h.setTitle(a.title);
        h.setContentText(a.contentText);
        h.setSourceType(a.sourceType);
        h.setFileAssetId(a.fileAssetId);
        h.setType(a.type);
        return h;
    }

    private static class CommentContext {
        private String text;
        private Long primaryCommentId;
        private Integer primaryChunkIndex;
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
