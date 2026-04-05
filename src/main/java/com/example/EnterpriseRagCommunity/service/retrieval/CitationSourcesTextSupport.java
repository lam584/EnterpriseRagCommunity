package com.example.EnterpriseRagCommunity.service.retrieval;

import java.util.List;
import java.util.function.Consumer;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;

public final class CitationSourcesTextSupport {

    private CitationSourcesTextSupport() {
    }

    public static <T> String renderSourcesText(
            CitationConfigDTO cfg,
            List<T> sources,
            String mode,
            Consumer<StringBuilder> appendSource
    ) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return "";
        if (!"SOURCES_SECTION".equals(mode) && !"BOTH".equals(mode)) return "";
        if (sources == null || sources.isEmpty()) return "";
        if (cfg.getSourcesTitle() == null || cfg.getSourcesTitle().isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.getSourcesTitle().trim()).append("：\n");
        appendSource.accept(sb);
        return sb.toString().trim();
    }
}
