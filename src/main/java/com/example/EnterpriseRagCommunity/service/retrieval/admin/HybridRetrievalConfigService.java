package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HybridRetrievalConfigService {

    public static final String KEY_CONFIG_JSON = "retrieval.hybrid.config.json";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public HybridRetrievalConfigDTO getConfig() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) return defaultConfig();
        try {
            HybridRetrievalConfigDTO cfg = objectMapper.readValue(json, HybridRetrievalConfigDTO.class);
            return normalize(cfg);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    @Transactional(readOnly = true)
    public HybridRetrievalConfigDTO getConfigOrDefault() {
        return getConfig();
    }

    @Transactional
    public HybridRetrievalConfigDTO updateConfig(HybridRetrievalConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        HybridRetrievalConfigDTO cfg = normalize(payload);
        try {
            String json = objectMapper.writeValueAsString(cfg);
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
        return cfg;
    }

    public HybridRetrievalConfigDTO normalizeConfig(HybridRetrievalConfigDTO payloadOrNull) {
        if (payloadOrNull == null) return normalize(defaultConfig());
        return normalize(payloadOrNull);
    }

    public HybridRetrievalConfigDTO defaultConfig() {
        HybridRetrievalConfigDTO dto = new HybridRetrievalConfigDTO();
        dto.setEnabled(true);

        dto.setBm25K(50);
        dto.setBm25TitleBoost(2.0);
        dto.setBm25ContentBoost(1.0);

        dto.setVecK(50);

        dto.setHybridK(30);
        dto.setFusionMode("RRF");
        dto.setBm25Weight(1.0);
        dto.setVecWeight(1.0);
        dto.setRrfK(60);

        dto.setRerankEnabled(true);
        dto.setRerankModel(null);
        dto.setRerankTemperature(0.0);
        dto.setRerankK(30);

        dto.setMaxDocs(500);
        dto.setPerDocMaxTokens(4000);
        dto.setMaxInputTokens(30000);
        return dto;
    }

    private HybridRetrievalConfigDTO normalize(HybridRetrievalConfigDTO in) {
        HybridRetrievalConfigDTO dto = (in == null) ? new HybridRetrievalConfigDTO() : in;

        dto.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));

        int bm25K = clampInt(dto.getBm25K(), 0, 1000, 50);
        int vecK = clampInt(dto.getVecK(), 0, 1000, 50);
        int hybridK = clampInt(dto.getHybridK(), 1, 1000, 30);

        dto.setBm25K(bm25K);
        dto.setVecK(vecK);
        dto.setHybridK(hybridK);

        dto.setBm25TitleBoost(clampDouble(dto.getBm25TitleBoost(), 0.1, 10.0, 2.0));
        dto.setBm25ContentBoost(clampDouble(dto.getBm25ContentBoost(), 0.1, 10.0, 1.0));

        String fusion = dto.getFusionMode() == null ? "" : dto.getFusionMode().trim().toUpperCase();
        if (!fusion.equals("RRF") && !fusion.equals("LINEAR")) fusion = "RRF";
        dto.setFusionMode(fusion);

        dto.setBm25Weight(clampDouble(dto.getBm25Weight(), 0.0, 100.0, 1.0));
        dto.setVecWeight(clampDouble(dto.getVecWeight(), 0.0, 100.0, 1.0));
        dto.setRrfK(clampInt(dto.getRrfK(), 1, 1000, 60));

        dto.setRerankEnabled(Boolean.TRUE.equals(dto.getRerankEnabled()));
        String rm = dto.getRerankModel();
        dto.setRerankModel(rm == null || rm.isBlank() ? null : rm.trim());
        dto.setRerankTemperature(clampDouble(dto.getRerankTemperature(), 0.0, 2.0, 0.0));
        dto.setRerankK(clampInt(dto.getRerankK(), 0, 500, 30));

        dto.setMaxDocs(clampInt(dto.getMaxDocs(), 1, 5000, 500));
        dto.setPerDocMaxTokens(clampInt(dto.getPerDocMaxTokens(), 100, 100_000, 4000));
        dto.setMaxInputTokens(clampInt(dto.getMaxInputTokens(), 1000, 1_000_000, 30000));

        if (dto.getRerankEnabled() && dto.getRerankK() > dto.getMaxDocs()) {
            dto.setRerankK(dto.getMaxDocs());
        }
        if (dto.getHybridK() > dto.getMaxDocs()) {
            dto.setHybridK(dto.getMaxDocs());
        }

        return dto;
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static double clampDouble(Double v, double min, double max, double def) {
        double x = v == null ? def : v;
        if (Double.isNaN(x) || Double.isInfinite(x)) x = def;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }
}
