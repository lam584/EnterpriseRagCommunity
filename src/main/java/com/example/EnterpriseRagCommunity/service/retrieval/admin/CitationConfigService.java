package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CitationConfigService {

    public static final String KEY_CONFIG_JSON = "retrieval.citation.config.json";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CitationConfigDTO getConfig() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) return defaultConfig();
        try {
            CitationConfigDTO cfg = objectMapper.readValue(json, CitationConfigDTO.class);
            return normalize(cfg);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    @Transactional(readOnly = true)
    public CitationConfigDTO getConfigOrDefault() {
        return getConfig();
    }

    @Transactional
    public CitationConfigDTO updateConfig(CitationConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        CitationConfigDTO cfg = normalize(payload);
        try {
            String json = objectMapper.writeValueAsString(cfg);
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
        return cfg;
    }

    public CitationConfigDTO normalizeConfig(CitationConfigDTO payloadOrNull) {
        if (payloadOrNull == null) return normalize(defaultConfig());
        return normalize(payloadOrNull);
    }

    public CitationConfigDTO defaultConfig() {
        CitationConfigDTO dto = new CitationConfigDTO();
        dto.setEnabled(true);
        dto.setCitationMode("MODEL_INLINE");
        dto.setInstructionTemplate("回答时在引用资料的句子末尾使用 [n] 引用编号（n 对应参考资料序号）；如资料不足请明确说明。");
        dto.setSourcesTitle("来源");
        dto.setMaxSources(6);
        dto.setIncludeUrl(true);
        dto.setIncludeScore(false);
        dto.setIncludeTitle(true);
        dto.setIncludePostId(false);
        dto.setIncludeChunkIndex(false);
        dto.setPostUrlTemplate("/portal/posts/detail/{postId}");
        return dto;
    }

    private CitationConfigDTO normalize(CitationConfigDTO in) {
        CitationConfigDTO dto = (in == null) ? new CitationConfigDTO() : in;

        dto.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));

        String mode = dto.getCitationMode() == null ? "" : dto.getCitationMode().trim().toUpperCase();
        if (!mode.equals("MODEL_INLINE") && !mode.equals("SOURCES_SECTION") && !mode.equals("BOTH")) mode = "MODEL_INLINE";
        dto.setCitationMode(mode);

        String ins = trimOrNull(dto.getInstructionTemplate());
        dto.setInstructionTemplate(ins == null ? defaultConfig().getInstructionTemplate() : ins);

        String st = trimOrNull(dto.getSourcesTitle());
        dto.setSourcesTitle(st == null ? defaultConfig().getSourcesTitle() : st);

        dto.setMaxSources(clampInt(dto.getMaxSources(), 0, 200, 6));

        dto.setIncludeUrl(Boolean.TRUE.equals(dto.getIncludeUrl()));
        dto.setIncludeScore(Boolean.TRUE.equals(dto.getIncludeScore()));
        dto.setIncludeTitle(Boolean.TRUE.equals(dto.getIncludeTitle()));
        dto.setIncludePostId(Boolean.TRUE.equals(dto.getIncludePostId()));
        dto.setIncludeChunkIndex(Boolean.TRUE.equals(dto.getIncludeChunkIndex()));

        String tpl = trimOrNull(dto.getPostUrlTemplate());
        dto.setPostUrlTemplate(tpl == null ? defaultConfig().getPostUrlTemplate() : tpl);

        return dto;
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
}

