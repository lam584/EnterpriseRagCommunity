package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;

import java.util.Locale;

public final class CitationRenderSupport {

    private CitationRenderSupport() {
    }

    public static void appendSourceLine(StringBuilder sb,
                                        CitationConfigDTO cfg,
                                        Integer index,
                                        String title,
                                        String url,
                                        Double score,
                                        Long postId,
                                        Long commentId,
                                        Integer chunkIndex) {
        sb.append('[').append(index == null ? "" : index).append("] ");
        if (Boolean.TRUE.equals(cfg.getIncludeTitle()) && title != null && !title.isBlank()) {
            sb.append(title.trim()).append(' ');
        }
        if (Boolean.TRUE.equals(cfg.getIncludeUrl()) && url != null && !url.isBlank()) {
            sb.append(url.trim()).append(' ');
        }
        if (Boolean.TRUE.equals(cfg.getIncludeScore()) && score != null) {
            sb.append("score=").append(String.format(Locale.ROOT, "%.4f", score)).append(' ');
        }
        if (Boolean.TRUE.equals(cfg.getIncludePostId()) && postId != null) {
            sb.append("post_id=").append(postId).append(' ');
        }
        if (commentId != null) {
            sb.append("comment_id=").append(commentId).append(' ');
        }
        if (Boolean.TRUE.equals(cfg.getIncludeChunkIndex()) && chunkIndex != null) {
            sb.append("chunk=").append(chunkIndex).append(' ');
        }
        sb.append('\n');
    }
}
