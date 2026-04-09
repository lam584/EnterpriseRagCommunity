package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.HotScoreConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HotScoreConfigService {
    public static final String KEY_HOT_SCORE_CONFIG_V1 = "hot_score_config_v1";

    public static final double DEFAULT_LIKE_WEIGHT = 1.0;
    public static final double DEFAULT_FAVORITE_WEIGHT = 2.0;
    public static final double DEFAULT_COMMENT_WEIGHT = 3.0;
    public static final double DEFAULT_VIEW_WEIGHT = 0.25;
    public static final double DEFAULT_ALL_DECAY_DAYS = 30.0;
    public static final boolean DEFAULT_AUTO_REFRESH_ENABLED = true;
    public static final int DEFAULT_AUTO_REFRESH_INTERVAL_MINUTES = 60;

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public HotScoreConfigDTO getConfigOrDefault() {
        String raw = appSettingsService.getString(KEY_HOT_SCORE_CONFIG_V1).orElse(null);
        HotScoreConfigDTO parsed = null;
        if (raw != null && !raw.isBlank()) {
            try {
                parsed = objectMapper.readValue(raw, HotScoreConfigDTO.class);
            } catch (Exception ignored) {
            }
        }
        return normalizeAndValidate(parsed, false);
    }

    @Transactional
    public HotScoreConfigDTO upsertConfig(HotScoreConfigDTO payload) {
        HotScoreConfigDTO normalized = normalizeAndValidate(payload, true);
        try {
            appSettingsService.upsertString(KEY_HOT_SCORE_CONFIG_V1, objectMapper.writeValueAsString(normalized));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("热度配置序列化失败");
        }
        return normalized;
    }

    private static HotScoreConfigDTO normalizeAndValidate(HotScoreConfigDTO input, boolean strict) {
        HotScoreConfigDTO out = new HotScoreConfigDTO();
        out.setLikeWeight(normalizePositive(input == null ? null : input.getLikeWeight(), DEFAULT_LIKE_WEIGHT, 100.0, "likeWeight", strict));
        out.setFavoriteWeight(normalizePositive(input == null ? null : input.getFavoriteWeight(), DEFAULT_FAVORITE_WEIGHT, 100.0, "favoriteWeight", strict));
        out.setCommentWeight(normalizePositive(input == null ? null : input.getCommentWeight(), DEFAULT_COMMENT_WEIGHT, 100.0, "commentWeight", strict));
        out.setViewWeight(normalizePositive(input == null ? null : input.getViewWeight(), DEFAULT_VIEW_WEIGHT, 10.0, "viewWeight", strict));
        out.setAllDecayDays(normalizePositive(input == null ? null : input.getAllDecayDays(), DEFAULT_ALL_DECAY_DAYS, 3650.0, "allDecayDays", strict));
        out.setAutoRefreshEnabled(input == null || input.getAutoRefreshEnabled() == null
            ? DEFAULT_AUTO_REFRESH_ENABLED
            : input.getAutoRefreshEnabled());
        out.setAutoRefreshIntervalMinutes(normalizeIntRange(
            input == null ? null : input.getAutoRefreshIntervalMinutes(),
            DEFAULT_AUTO_REFRESH_INTERVAL_MINUTES,
            1,
            1440,
            "autoRefreshIntervalMinutes",
            strict
        ));
        return out;
    }

    private static Double normalizePositive(Double value, double defaultValue, double maxValue, String field, boolean strict) {
        if (value == null) return defaultValue;
        if (!Double.isFinite(value)) {
            if (strict) throw new IllegalArgumentException(field + " 必须是有限数值");
            return defaultValue;
        }
        if (value <= 0 || value > maxValue) {
            if (strict) throw new IllegalArgumentException(field + " 取值范围应为 (0, " + maxValue + "]");
            return Math.clamp(value, Double.MIN_VALUE, maxValue);
        }
        return value;
    }

    private static Integer normalizeIntRange(Integer value, int defaultValue, int minValue, int maxValue, String field, boolean strict) {
        if (value == null) return defaultValue;
        if (value < minValue || value > maxValue) {
            if (strict) throw new IllegalArgumentException(field + " 取值范围应为 [" + minValue + ", " + maxValue + "]");
            return Math.clamp(value, minValue, maxValue);
        }
        return value;
    }
}
