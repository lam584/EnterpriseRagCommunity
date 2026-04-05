package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationTestResponse;
import com.example.EnterpriseRagCommunity.service.retrieval.CitationSourcesTextSupport;
import com.example.EnterpriseRagCommunity.service.retrieval.CitationUrlSupport;
import com.example.EnterpriseRagCommunity.service.retrieval.CitationModeSupport;
import com.example.EnterpriseRagCommunity.service.retrieval.CitationRenderSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
@Service
@RequiredArgsConstructor
public class CitationTestService {

    private final CitationConfigService citationConfigService;

    private static String normalizeCitationMode(CitationConfigDTO cfg) {
        return CitationModeSupport.normalizeCitationMode(cfg);
    }

    private static String buildPostUrl(CitationConfigDTO cfg, Long postId) {
        return CitationUrlSupport.buildPostUrl(cfg, postId);
    }

    private static String renderInstructionPreview(CitationConfigDTO cfg) {
        if (cfg == null) return null;
        if (!Boolean.TRUE.equals(cfg.getEnabled())) return "";
        String mode = normalizeCitationMode(cfg);
        if (!mode.equals("MODEL_INLINE") && !mode.equals("BOTH")) return "";
        return cfg.getInstructionTemplate();
    }

    private static String renderSourcesText(CitationConfigDTO cfg, List<CitationTestResponse.Source> sources) {
        String mode = normalizeCitationMode(cfg);
        return CitationSourcesTextSupport.renderSourcesText(cfg, sources, mode, sb -> {
            for (CitationTestResponse.Source s : sources) {
                if (s == null) continue;
                CitationRenderSupport.appendSourceLine(
                        sb,
                        cfg,
                        s.getIndex(),
                        s.getTitle(),
                        s.getUrl(),
                        s.getScore(),
                        s.getPostId(),
                        null,
                        s.getChunkIndex()
                );
            }
        });
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
