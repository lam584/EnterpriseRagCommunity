package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;

import java.util.Locale;

public final class CitationModeSupport {

    private CitationModeSupport() {
    }

    public static String normalizeCitationMode(CitationConfigDTO citationCfg) {
        if (citationCfg == null) return "MODEL_INLINE";
        String mode = citationCfg.getCitationMode() == null
                ? ""
                : citationCfg.getCitationMode().trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("MODEL_INLINE") && !mode.equals("SOURCES_SECTION") && !mode.equals("BOTH")) {
            return "MODEL_INLINE";
        }
        return mode;
    }
}
