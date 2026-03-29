package com.example.EnterpriseRagCommunity.service.ai;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;

final class AiChatRetrievalSupport {

    private AiChatRetrievalSupport() {
    }

    static void appendCommentHits(List<RetrievalHitsEntity> out, Long eventId, List<RagCommentChatRetrievalService.Hit> hits) {
        if (eventId == null || hits == null || hits.isEmpty()) return;
        int n = Math.min(1000, hits.size());
        for (int i = 0; i < n; i++) {
            RagCommentChatRetrievalService.Hit h = hits.get(i);
            if (h == null) continue;
            RetrievalHitsEntity rh = new RetrievalHitsEntity();
            rh.setEventId(eventId);
            rh.setRank(i + 1);
            rh.setHitType(RetrievalHitType.COMMENT_VEC);
            rh.setPostId(h.getPostId());
            rh.setChunkId(null);
            rh.setScore(h.getScore() == null ? 0.0 : h.getScore());
            out.add(rh);
        }
    }

    static void writeRagDebugEvent(
            PrintWriter out,
            ChatRagAugmentConfigDTO cfg,
            String queryText,
            List<RagPostChatRetrievalService.Hit> aggHits,
            List<RagCommentChatRetrievalService.Hit> commentHits,
            RagContextPromptService.AssembleResult contextAssembled
    ) {
        if (out == null || cfg == null || !Boolean.TRUE.equals(cfg.getDebugEnabled())) return;
        int maxChars = cfg.getDebugMaxChars() == null ? 4000 : Math.clamp(cfg.getDebugMaxChars(), 0, 200_000);
        if (maxChars <= 0) return;

        Map<Long, RagPostChatRetrievalService.Hit> aggByPostId = new java.util.HashMap<>();
        if (aggHits != null) {
            for (RagPostChatRetrievalService.Hit h : aggHits) {
                if (h == null || h.getPostId() == null) continue;
                aggByPostId.putIfAbsent(h.getPostId(), h);
            }
        }

        List<RagContextPromptService.Item> selected = contextAssembled == null ? List.of() : (contextAssembled.getSelected() == null ? List.of() : contextAssembled.getSelected());
        int perItemMax = Math.clamp(maxChars / Math.max(1, selected.size()), 200, 2000);

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"query\":\"").append(AiChatJsonSupport.jsonEscape(queryText == null ? "" : queryText)).append('"');

        sb.append(",\"selected\":[");
        int totalPreview = 0;
        for (RagContextPromptService.Item it : selected) {
            if (it == null) continue;
            if (sb.charAt(sb.length() - 1) != '[') sb.append(',');
            sb.append('{');
            sb.append("\"rank\":").append(it.getRank() == null ? "null" : it.getRank());
            sb.append(",\"postId\":").append(it.getPostId() == null ? "null" : it.getPostId());
            sb.append(",\"commentId\":").append(it.getCommentId() == null ? "null" : it.getCommentId());
            sb.append(",\"score\":").append(it.getScore() == null ? "null" : String.format(Locale.ROOT, "%.6f", it.getScore()));
            String preview = "";
            if (it.getPostId() != null) {
                RagPostChatRetrievalService.Hit h = aggByPostId.get(it.getPostId());
                String t = h == null ? null : h.getContentText();
                if (t != null) {
                    String trimmed = t.trim();
                    int remain = Math.max(0, maxChars - totalPreview);
                    int cap = Math.min(perItemMax, remain);
                    if (cap > 0) {
                        preview = trimmed.length() <= cap ? trimmed : trimmed.substring(0, cap);
                        totalPreview += preview.length();
                    }
                }
            }
            sb.append(",\"preview\":\"").append(AiChatJsonSupport.jsonEscape(preview)).append('"');
            sb.append('}');
            if (totalPreview >= maxChars) break;
        }
        sb.append(']');

        sb.append(",\"commentHits\":[");
        int n = Math.min(50, commentHits == null ? 0 : commentHits.size());
        for (int i = 0; i < n; i++) {
            RagCommentChatRetrievalService.Hit h = commentHits.get(i);
            if (h == null) continue;
            if (sb.charAt(sb.length() - 1) != '[') sb.append(',');
            sb.append('{');
            sb.append("\"commentId\":").append(h.getCommentId() == null ? "null" : h.getCommentId());
            sb.append(",\"postId\":").append(h.getPostId() == null ? "null" : h.getPostId());
            sb.append(",\"score\":").append(h.getScore() == null ? "null" : String.format(Locale.ROOT, "%.6f", h.getScore()));
            sb.append('}');
        }
        sb.append(']');

        sb.append('}');

        out.write("event: rag_debug\n");
        out.write("data: " + sb + "\n\n");
        out.flush();
    }

    static List<RagPostChatRetrievalService.Hit> toRagHits(List<HybridRagRetrievalService.DocHit> hits) {
        if (hits == null || hits.isEmpty()) return List.of();
        List<RagPostChatRetrievalService.Hit> out = new ArrayList<>();
        for (HybridRagRetrievalService.DocHit h : hits) {
            if (h == null) continue;
            RagPostChatRetrievalService.Hit rr = new RagPostChatRetrievalService.Hit();
            rr.setDocId(h.getDocId());
            rr.setSourceType(h.getSourceType());
            rr.setFileAssetId(h.getFileAssetId());
            Double s = h.getRerankScore();
            if (s == null) s = h.getFusedScore();
            if (s == null) s = h.getScore();
            rr.setScore(s);
            Long postId = h.getPostId();
            if (postId == null && h.getPostIds() != null && !h.getPostIds().isEmpty()) {
                postId = h.getPostIds().getFirst();
            }
            rr.setPostId(postId);
            rr.setChunkIndex(h.getChunkIndex());
            rr.setBoardId(h.getBoardId());
            rr.setTitle(h.getTitle());
            rr.setContentText(h.getContentText());
            out.add(rr);
        }
        return out;
    }

    static void appendStageHits(List<RetrievalHitsEntity> out, Long eventId, RetrievalHitType type, List<HybridRagRetrievalService.DocHit> hits) {
        if (eventId == null || hits == null || hits.isEmpty()) return;
        int n = Math.min(1000, hits.size());
        for (int i = 0; i < n; i++) {
            HybridRagRetrievalService.DocHit h = hits.get(i);
            if (h == null) continue;
            RetrievalHitsEntity rh = new RetrievalHitsEntity();
            rh.setEventId(eventId);
            rh.setRank(i + 1);
            rh.setHitType(type);
            Long postId = h.getPostId();
            if (postId == null && h.getPostIds() != null && !h.getPostIds().isEmpty()) {
                postId = h.getPostIds().getFirst();
            }
            rh.setPostId(postId);
            rh.setChunkId(null);
            Double s = h.getScore();
            if (type == RetrievalHitType.BM25 && h.getBm25Score() != null) s = h.getBm25Score();
            if (type == RetrievalHitType.VEC && h.getVecScore() != null) s = h.getVecScore();
            if (type == RetrievalHitType.RERANK && h.getRerankScore() != null) s = h.getRerankScore();
            if (s == null) s = 0.0;
            rh.setScore(s);
            out.add(rh);
        }
    }

    static void appendChatHits(List<RetrievalHitsEntity> out, Long eventId, RetrievalHitType type, List<RagPostChatRetrievalService.Hit> hits) {
        if (eventId == null || hits == null || hits.isEmpty()) return;
        int n = Math.min(1000, hits.size());
        for (int i = 0; i < n; i++) {
            RagPostChatRetrievalService.Hit h = hits.get(i);
            if (h == null) continue;
            RetrievalHitsEntity rh = new RetrievalHitsEntity();
            rh.setEventId(eventId);
            rh.setRank(i + 1);
            rh.setHitType(type);
            rh.setPostId(h.getPostId());
            rh.setChunkId(null);
            rh.setScore(h.getScore() == null ? 0.0 : h.getScore());
            out.add(rh);
        }
    }

    static void sanitizeHitChunkIds(List<RetrievalHitsEntity> hits) {
        if (hits == null || hits.isEmpty()) return;
        for (RetrievalHitsEntity h : hits) {
            if (h == null) continue;
            h.setChunkId(null);
        }
    }
}
