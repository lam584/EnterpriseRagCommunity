package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationTestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CitationTestService {

    private final CitationConfigService citationConfigService;

    private static String normalizeCitationMode(CitationConfigDTO cfg) {
        if (cfg == null) return "MODEL_INLINE";
        String mode = cfg.getCitationMode() == null ? "" : cfg.getCitationMode().trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("MODEL_INLINE") && !mode.equals("SOURCES_SECTION") && !mode.equals("BOTH")) {
            return "MODEL_INLINE";
        }
        return mode;
    }

    private static String buildPostUrl(CitationConfigDTO cfg, Long postId) {
        if (cfg == null) return null;
        String tpl = cfg.getPostUrlTemplate();
        if (tpl == null || tpl.isBlank()) return null;
        String id = postId == null ? "" : String.valueOf(postId);
        return tpl.replace("{postId}", id);
    }

    private static String renderInstructionPreview(CitationConfigDTO cfg) {
        if (cfg == null) return null;
        if (!Boolean.TRUE.equals(cfg.getEnabled())) return "";
        String mode = normalizeCitationMode(cfg);
        if (!mode.equals("MODEL_INLINE") && !mode.equals("BOTH")) return "";
        return cfg.getInstructionTemplate();
    }

    private static String renderSourcesText(CitationConfigDTO cfg, List<CitationTestResponse.Source> sources) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return "";
        String mode = normalizeCitationMode(cfg);
        if (!mode.equals("SOURCES_SECTION") && !mode.equals("BOTH")) return "";
        if (sources == null || sources.isEmpty()) return "";
        if (cfg.getSourcesTitle() == null || cfg.getSourcesTitle().isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.getSourcesTitle().trim()).append("：\n");
        for (CitationTestResponse.Source s : sources) {
            if (s == null) continue;
            sb.append('[').append(s.getIndex() == null ? "" : s.getIndex()).append("] ");
            if (Boolean.TRUE.equals(cfg.getIncludeTitle()) && s.getTitle() != null && !s.getTitle().isBlank()) {
                sb.append(s.getTitle().trim()).append(' ');
            }
            if (Boolean.TRUE.equals(cfg.getIncludeUrl()) && s.getUrl() != null && !s.getUrl().isBlank()) {
                sb.append(s.getUrl().trim()).append(' ');
            }
            if (Boolean.TRUE.equals(cfg.getIncludeScore()) && s.getScore() != null) {
                sb.append("score=").append(String.format(Locale.ROOT, "%.4f", s.getScore())).append(' ');
            }
            if (Boolean.TRUE.equals(cfg.getIncludePostId()) && s.getPostId() != null) {
                sb.append("post_id=").append(s.getPostId()).append(' ');
            }
            if (Boolean.TRUE.equals(cfg.getIncludeChunkIndex()) && s.getChunkIndex() != null) {
                sb.append("chunk=").append(s.getChunkIndex()).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public CitationTestResponse test(CitationTestRequest req) {
        CitationConfigDTO cfg;
        if (req == null || Boolean.TRUE.equals(req.getUseSavedConfig()) || req.getConfig() == null) {
            cfg = citationConfigService.getConfigOrDefault();
        } else {
            cfg = citationConfigService.normalizeConfig(req.getConfig());
        }

        CitationTestResponse out = new CitationTestResponse();
        out.setConfig(cfg);
        out.setInstructionPreview(renderInstructionPreview(cfg));

        List<CitationTestResponse.Source> sources = new ArrayList<>();
        List<CitationTestRequest.CitationTestItem> items = req == null ? null : req.getItems();
        int max = cfg == null || cfg.getMaxSources() == null ? 0 : Math.max(0, cfg.getMaxSources());
        int n = Math.min(max, items == null ? 0 : items.size());
        for (int i = 0; i < n; i++) {
            CitationTestRequest.CitationTestItem it = items.get(i);
            if (it == null) continue;
            CitationTestResponse.Source s = new CitationTestResponse.Source();
            s.setIndex(i + 1);
            s.setPostId(it.getPostId());
            s.setChunkIndex(it.getChunkIndex());
            s.setScore(it.getScore());
            s.setTitle(it.getTitle());
            s.setUrl(buildPostUrl(cfg, it.getPostId()));
            sources.add(s);
        }
        out.setSources(sources);
        out.setSourcesPreview(renderSourcesText(cfg, sources));
        return out;
    }
}

