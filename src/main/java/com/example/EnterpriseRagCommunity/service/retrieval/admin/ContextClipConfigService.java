package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContextClipConfigService {

    public static final String KEY_CONFIG_JSON = "retrieval.context.config.json";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ContextClipConfigDTO getConfig() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) return defaultConfig();
        try {
            ContextClipConfigDTO cfg = objectMapper.readValue(json, ContextClipConfigDTO.class);
            return normalize(cfg);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    @Transactional(readOnly = true)
    public ContextClipConfigDTO getConfigOrDefault() {
        return getConfig();
    }

    @Transactional
    public ContextClipConfigDTO updateConfig(ContextClipConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        ContextClipConfigDTO cfg = normalize(payload);
        try {
            String json = objectMapper.writeValueAsString(cfg);
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
        return cfg;
    }

    public ContextClipConfigDTO normalizeConfig(ContextClipConfigDTO payloadOrNull) {
        if (payloadOrNull == null) return normalize(defaultConfig());
        return normalize(payloadOrNull);
    }

    public ContextClipConfigDTO defaultConfig() {
        ContextClipConfigDTO dto = new ContextClipConfigDTO();
        dto.setEnabled(true);
        dto.setPolicy(ContextWindowPolicy.TOPK);

        dto.setMaxItems(6);
        dto.setMaxContextTokens(12000);
        dto.setReserveAnswerTokens(2000);
        dto.setPerItemMaxTokens(2000);
        dto.setMaxPromptChars(200000);

        dto.setMinScore(null);
        dto.setMaxSamePostItems(2);
        dto.setRequireTitle(false);

        dto.setDedupByPostId(true);
        dto.setDedupByTitle(false);
        dto.setDedupByContentHash(true);

        dto.setSectionTitle("以下为从社区帖子检索到的参考资料（仅供参考，回答时请结合用户问题，不要编造不存在的来源）：");
        dto.setItemHeaderTemplate("[{i}] post_id={postId} chunk={chunkIndex} score={score}\n标题：{title}\n");
        dto.setSeparator("\n\n");

        dto.setShowPostId(true);
        dto.setShowChunkIndex(true);
        dto.setShowScore(true);
        dto.setShowTitle(true);

        dto.setExtraInstruction("回答时尽量在相关句末添加 [编号] 引用；如资料不足请明确说明。");

        dto.setLogEnabled(true);
        dto.setLogSampleRate(1.0);
        dto.setLogMaxDays(30);
        return dto;
    }

    private ContextClipConfigDTO normalize(ContextClipConfigDTO in) {
        ContextClipConfigDTO dto = (in == null) ? new ContextClipConfigDTO() : in;

        dto.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));

        ContextWindowPolicy policy = dto.getPolicy();
        if (policy == null) policy = ContextWindowPolicy.TOPK;
        dto.setPolicy(policy);

        dto.setMaxItems(clampInt(dto.getMaxItems(), 1, 100, 6));
        dto.setMaxContextTokens(clampInt(dto.getMaxContextTokens(), 100, 1_000_000, 12000));
        dto.setReserveAnswerTokens(clampInt(dto.getReserveAnswerTokens(), 0, 1_000_000, 2000));
        dto.setPerItemMaxTokens(clampInt(dto.getPerItemMaxTokens(), 50, 200_000, 2000));
        dto.setMaxPromptChars(clampInt(dto.getMaxPromptChars(), 1000, 2_000_000, 200000));

        dto.setMinScore(clampDoubleNullable(dto.getMinScore(), -1e9, 1e9));
        dto.setMaxSamePostItems(clampInt(dto.getMaxSamePostItems(), 0, 100, 2));
        dto.setRequireTitle(Boolean.TRUE.equals(dto.getRequireTitle()));

        dto.setDedupByPostId(Boolean.TRUE.equals(dto.getDedupByPostId()));
        dto.setDedupByTitle(Boolean.TRUE.equals(dto.getDedupByTitle()));
        dto.setDedupByContentHash(Boolean.TRUE.equals(dto.getDedupByContentHash()));

        String title = trimOrNull(dto.getSectionTitle());
        dto.setSectionTitle(title == null ? defaultConfig().getSectionTitle() : title);

        String headerTpl = trimOrNull(dto.getItemHeaderTemplate());
        dto.setItemHeaderTemplate(headerTpl == null ? defaultConfig().getItemHeaderTemplate() : headerTpl);

        String sep = dto.getSeparator();
        dto.setSeparator(sep == null ? "\n\n" : sep);

        dto.setShowPostId(Boolean.TRUE.equals(dto.getShowPostId()));
        dto.setShowChunkIndex(Boolean.TRUE.equals(dto.getShowChunkIndex()));
        dto.setShowScore(Boolean.TRUE.equals(dto.getShowScore()));
        dto.setShowTitle(Boolean.TRUE.equals(dto.getShowTitle()));

        String ins = trimOrNull(dto.getExtraInstruction());
        dto.setExtraInstruction(ins);

        dto.setLogEnabled(Boolean.TRUE.equals(dto.getLogEnabled()));
        dto.setLogSampleRate(clampDouble(dto.getLogSampleRate(), 0.0, 1.0, 1.0));
        dto.setLogMaxDays(clampInt(dto.getLogMaxDays(), 1, 3650, 30));

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

    private static Double clampDoubleNullable(Double v, double min, double max) {
        if (v == null) return null;
        double x = v;
        if (Double.isNaN(x) || Double.isInfinite(x)) return null;
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

