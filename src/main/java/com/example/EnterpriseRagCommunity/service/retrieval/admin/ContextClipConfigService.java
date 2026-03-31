package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ContextClipConfigService {

    public static final String KEY_CONFIG_JSON = "retrieval.context.config.json";
    public static final String ABLATION_NONE = "NONE";
    public static final String ABLATION_REL_ONLY = "REL_ONLY";
    public static final String ABLATION_REL_IMP = "REL_IMP";
    public static final String ABLATION_REL_IMP_RED = "REL_IMP_RED";

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
        dto.setContextTokenBudget(3000);

        dto.setMinScore(null);
        dto.setMaxSamePostItems(2);
        dto.setRequireTitle(false);
        dto.setAlpha(1.0);
        dto.setBeta(1.0);
        dto.setGamma(1.0);
        dto.setAblationMode(ABLATION_REL_IMP_RED);
        dto.setCrossSourceDedup(true);

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
        ContextClipConfigDTO defaults = defaultConfig();

        dto.setEnabled(boolOrDefault(dto.getEnabled(), Boolean.TRUE.equals(defaults.getEnabled())));

        ContextWindowPolicy policy = dto.getPolicy();
        if (policy == null) policy = defaults.getPolicy();
        dto.setPolicy(policy);

        dto.setMaxItems(clampInt(dto.getMaxItems(), 1, 100, defaults.getMaxItems()));
        Integer rawBudget = dto.getContextTokenBudget();
        if (rawBudget == null) rawBudget = dto.getMaxContextTokens();
        int normalizedBudget = clampInt(rawBudget, 100, 1_000_000, defaults.getContextTokenBudget());
        dto.setContextTokenBudget(normalizedBudget);
        Integer rawMaxContextTokens = dto.getMaxContextTokens();
        if (rawMaxContextTokens == null) rawMaxContextTokens = normalizedBudget;
        dto.setMaxContextTokens(clampInt(rawMaxContextTokens, 100, 1_000_000, defaults.getMaxContextTokens()));
        dto.setReserveAnswerTokens(clampInt(dto.getReserveAnswerTokens(), 0, 1_000_000, defaults.getReserveAnswerTokens()));
        dto.setPerItemMaxTokens(clampInt(dto.getPerItemMaxTokens(), 50, 200_000, defaults.getPerItemMaxTokens()));
        dto.setMaxPromptChars(clampInt(dto.getMaxPromptChars(), 1000, 2_000_000, defaults.getMaxPromptChars()));

        dto.setMinScore(clampDoubleNullable(dto.getMinScore(), -1e9, 1e9));
        dto.setMaxSamePostItems(clampInt(dto.getMaxSamePostItems(), 0, 100, defaults.getMaxSamePostItems()));
        dto.setRequireTitle(boolOrDefault(dto.getRequireTitle(), Boolean.TRUE.equals(defaults.getRequireTitle())));
        dto.setAlpha(clampDouble(dto.getAlpha(), 0.0, 10.0, defaults.getAlpha()));
        dto.setBeta(clampDouble(dto.getBeta(), 0.0, 10.0, defaults.getBeta()));
        dto.setGamma(clampDouble(dto.getGamma(), 0.0, 10.0, defaults.getGamma()));
        dto.setAblationMode(normalizeAblationMode(dto.getAblationMode(), defaults.getAblationMode()));
        dto.setCrossSourceDedup(boolOrDefault(dto.getCrossSourceDedup(), Boolean.TRUE.equals(defaults.getCrossSourceDedup())));

        dto.setDedupByPostId(boolOrDefault(dto.getDedupByPostId(), Boolean.TRUE.equals(defaults.getDedupByPostId())));
        dto.setDedupByTitle(boolOrDefault(dto.getDedupByTitle(), Boolean.TRUE.equals(defaults.getDedupByTitle())));
        dto.setDedupByContentHash(boolOrDefault(dto.getDedupByContentHash(), Boolean.TRUE.equals(defaults.getDedupByContentHash())));

        String title = trimOrNull(dto.getSectionTitle());
        dto.setSectionTitle(title == null ? defaults.getSectionTitle() : title);

        String headerTpl = trimOrNull(dto.getItemHeaderTemplate());
        dto.setItemHeaderTemplate(headerTpl == null ? defaults.getItemHeaderTemplate() : headerTpl);

        String sep = dto.getSeparator();
        dto.setSeparator(sep == null ? defaults.getSeparator() : sep);

        dto.setShowPostId(boolOrDefault(dto.getShowPostId(), Boolean.TRUE.equals(defaults.getShowPostId())));
        dto.setShowChunkIndex(boolOrDefault(dto.getShowChunkIndex(), Boolean.TRUE.equals(defaults.getShowChunkIndex())));
        dto.setShowScore(boolOrDefault(dto.getShowScore(), Boolean.TRUE.equals(defaults.getShowScore())));
        dto.setShowTitle(boolOrDefault(dto.getShowTitle(), Boolean.TRUE.equals(defaults.getShowTitle())));

        String ins = trimOrNull(dto.getExtraInstruction());
        dto.setExtraInstruction(ins);

        dto.setLogEnabled(boolOrDefault(dto.getLogEnabled(), Boolean.TRUE.equals(defaults.getLogEnabled())));
        dto.setLogSampleRate(clampDouble(dto.getLogSampleRate(), 0.0, 1.0, defaults.getLogSampleRate()));
        dto.setLogMaxDays(clampInt(dto.getLogMaxDays(), 1, 3650, defaults.getLogMaxDays()));

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

    private static boolean boolOrDefault(Boolean v, boolean def) {
        return v == null ? def : v;
    }

    private static String normalizeAblationMode(String mode, String def) {
        String x = trimOrNull(mode);
        if (x == null) return def;
        String key = x.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace('+', '_');
        return switch (key) {
            case "NONE", "NO_PRUNING" -> ABLATION_NONE;
            case "REL", "REL_ONLY" -> ABLATION_REL_ONLY;
            case "REL_IMP", "RELATIVE_IMPORTANCE" -> ABLATION_REL_IMP;
            case "REL_IMP_RED", "REL_IMP_REDUCTION", "REL_IMP_REDUNDANCY" -> ABLATION_REL_IMP_RED;
            default -> def;
        };
    }
}
