package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;

public final class CitationUrlSupport {

    private CitationUrlSupport() {
    }

    public static String buildPostUrl(CitationConfigDTO cfg, Long postId) {
        if (cfg == null) return null;
        String tpl = cfg.getPostUrlTemplate();
        if (tpl == null || tpl.isBlank()) return null;
        String id = postId == null ? "" : String.valueOf(postId);
        return tpl.replace("{postId}", id);
    }
}
