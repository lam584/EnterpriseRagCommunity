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

    public static final String KEY_CONFIG_JSON = "retrieval_hybrid_config";
    public static final String KEY_CONFIG_JSON_LEGACY = "retrieval.hybrid.config.json";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public HybridRetrievalConfigDTO getConfig() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) {
            json = appSettingsService.getString(KEY_CONFIG_JSON_LEGACY).orElse(null);
        }
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
            appSettingsService.upsertString(KEY_CONFIG_JSON_LEGACY, json);
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
        dto.setFileVecEnabled(true);
        dto.setFileVecK(30);

        dto.setHybridK(12);
        dto.setFusionMode("RRF");
        dto.setBm25Weight(1.0);
        dto.setVecWeight(1.0);
        dto.setFileVecWeight(1.0);
        dto.setRrfK(60);

        dto.setRerankEnabled(true);
        dto.setRerankModel(null);
        dto.setRerankTemperature(0.0);
        dto.setRerankTopP(0.2);
        dto.setRerankK(8);
        dto.setRerankTimeoutMs(12000);
        dto.setRerankSlowThresholdMs(6000);

        dto.setMaxDocs(500);
        dto.setPerDocMaxTokens(800);
        dto.setMaxInputTokens(8000);
        return dto;
    }

    private HybridRetrievalConfigDTO normalize(HybridRetrievalConfigDTO in) {
        HybridRetrievalConfigDTO dto = (in == null) ? new HybridRetrievalConfigDTO() : in;

        dto.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));

        int bm25K = clampInt(dto.getBm25K(), 0, 1000, 50);
        int vecK = clampInt(dto.getVecK(), 0, 1000, 50);
        boolean fileVecEnabled = dto.getFileVecEnabled() == null || dto.getFileVecEnabled();
        int fileVecK = clampInt(dto.getFileVecK(), 0, 1000, 30);
        if (!fileVecEnabled) fileVecK = 0;
        int hybridK = clampInt(dto.getHybridK(), 1, 1000, 30);

        dto.setBm25K(bm25K);
        dto.setVecK(vecK);
        dto.setFileVecEnabled(fileVecEnabled);
        dto.setFileVecK(fileVecK);
        dto.setHybridK(hybridK);

        dto.setBm25TitleBoost(clampDouble(dto.getBm25TitleBoost(), 0.1, 10.0, 2.0));
        dto.setBm25ContentBoost(clampDouble(dto.getBm25ContentBoost(), 0.1, 10.0, 1.0));

        String fusion = dto.getFusionMode() == null ? "" : dto.getFusionMode().trim().toUpperCase();
        if (!fusion.equals("RRF") && !fusion.equals("LINEAR")) fusion = "RRF";
        dto.setFusionMode(fusion);

        dto.setBm25Weight(clampDouble(dto.getBm25Weight(), 0.0, 100.0, 1.0));
        dto.setVecWeight(clampDouble(dto.getVecWeight(), 0.0, 100.0, 1.0));
        dto.setFileVecWeight(clampDouble(dto.getFileVecWeight(), 0.0, 100.0, 1.0));
        dto.setRrfK(clampInt(dto.getRrfK(), 1, 1000, 60));

        dto.setRerankEnabled(Boolean.TRUE.equals(dto.getRerankEnabled()));
        String rm = dto.getRerankModel();
        dto.setRerankModel(rm == null || rm.isBlank() ? null : rm.trim());
        dto.setRerankTemperature(clampDouble(dto.getRerankTemperature(), 0.0, 2.0, 0.0));
        dto.setRerankTopP(clampDouble(dto.getRerankTopP(), 0.0, 1.0, 0.2));
        dto.setRerankK(clampInt(dto.getRerankK(), 0, 500, 8));
        dto.setRerankTimeoutMs(clampInt(dto.getRerankTimeoutMs(), 1000, 120_000, 12_000));
        dto.setRerankSlowThresholdMs(clampInt(dto.getRerankSlowThresholdMs(), 100, 120_000, 6_000));

        dto.setMaxDocs(clampInt(dto.getMaxDocs(), 1, 5000, 500));
        dto.setPerDocMaxTokens(clampInt(dto.getPerDocMaxTokens(), 100, 100_000, 800));
        dto.setMaxInputTokens(clampInt(dto.getMaxInputTokens(), 1000, 1_000_000, 8000));

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
