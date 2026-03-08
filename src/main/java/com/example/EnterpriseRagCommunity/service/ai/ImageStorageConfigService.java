package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImageStorageConfigService {

    private final SystemConfigurationService systemConfigurationService;
    private static final String IMAGE_STORAGE_MODE_KEY = "image.storage.mode";

    // ── Storage mode ────────────────────────────────────

    public enum StorageMode { LOCAL, DASHSCOPE_TEMP, ALIYUN_OSS }

    public StorageMode getMode() {
        String raw = systemConfigurationService.getConfig(IMAGE_STORAGE_MODE_KEY);
        if (raw == null || raw.isBlank()) {
            systemConfigurationService.saveConfig(IMAGE_STORAGE_MODE_KEY, StorageMode.DASHSCOPE_TEMP.name(), false, "图片存储模式");
            return StorageMode.DASHSCOPE_TEMP;
        }
        try {
            return StorageMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return StorageMode.DASHSCOPE_TEMP;
        }
    }

    // ── Full config DTO ─────────────────────────────────

    public ImageStorageConfig getConfig() {
        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setMode(getMode().name());
        cfg.setLocalBaseUrl(getOrDefault("image.storage.local.base_url", ""));
        cfg.setDashscopeModel(getOrDefault("image.storage.dashscope.model", "qwen-vl-plus"));
        cfg.setOssEndpoint(getOrDefault("image.storage.oss.endpoint", ""));
        cfg.setOssBucket(getOrDefault("image.storage.oss.bucket", ""));
        cfg.setOssAccessKeyId(getOrDefault("image.storage.oss.access_key_id", ""));
        cfg.setOssAccessKeySecret(maskSecret(getOrDefault("image.storage.oss.access_key_secret", "")));
        cfg.setOssRegion(getOrDefault("image.storage.oss.region", ""));
        cfg.setCompressionEnabled(getBool("image.compression.enabled", true));
        cfg.setCompressionMaxWidth(getInt("image.compression.max_width", 1920));
        cfg.setCompressionMaxHeight(getInt("image.compression.max_height", 1920));
        cfg.setCompressionQuality(getDouble("image.compression.quality", 0.85));
        cfg.setCompressionMaxBytes(getInt("image.compression.max_bytes", 500_000));
        return cfg;
    }

    /** Return config with raw (unmasked) secrets — used internally for upload. */
    public ImageStorageConfig getConfigRaw() {
        ImageStorageConfig cfg = getConfig();
        cfg.setOssAccessKeySecret(getOrDefault("image.storage.oss.access_key_secret", ""));
        return cfg;
    }

    public void updateConfig(ImageStorageConfig cfg) {
        if (cfg.getMode() != null) {
            systemConfigurationService.saveConfig(IMAGE_STORAGE_MODE_KEY, cfg.getMode().trim().toUpperCase(), false, "图片存储模式");
        }
        saveIfPresent("image.storage.local.base_url", cfg.getLocalBaseUrl(), false);
        saveIfPresent("image.storage.dashscope.model", cfg.getDashscopeModel(), false);
        saveIfPresent("image.storage.oss.endpoint", cfg.getOssEndpoint(), true);
        saveIfPresent("image.storage.oss.bucket", cfg.getOssBucket(), false);
        saveIfPresent("image.storage.oss.access_key_id", cfg.getOssAccessKeyId(), true);
        // Only save secret if it's not masked
        if (cfg.getOssAccessKeySecret() != null && !cfg.getOssAccessKeySecret().contains("***")) {
            systemConfigurationService.saveConfig("image.storage.oss.access_key_secret", cfg.getOssAccessKeySecret(), true, "OSS AccessKey Secret");
        }
        saveIfPresent("image.storage.oss.region", cfg.getOssRegion(), false);
        if (cfg.getCompressionEnabled() != null) {
            systemConfigurationService.saveConfig("image.compression.enabled", String.valueOf(cfg.getCompressionEnabled()), false, "图片压缩开关");
        }
        if (cfg.getCompressionMaxWidth() != null) {
            systemConfigurationService.saveConfig("image.compression.max_width", String.valueOf(cfg.getCompressionMaxWidth()), false, "压缩最大宽度");
        }
        if (cfg.getCompressionMaxHeight() != null) {
            systemConfigurationService.saveConfig("image.compression.max_height", String.valueOf(cfg.getCompressionMaxHeight()), false, "压缩最大高度");
        }
        if (cfg.getCompressionQuality() != null) {
            systemConfigurationService.saveConfig("image.compression.quality", String.valueOf(cfg.getCompressionQuality()), false, "JPEG 压缩质量");
        }
        if (cfg.getCompressionMaxBytes() != null) {
            systemConfigurationService.saveConfig("image.compression.max_bytes", String.valueOf(cfg.getCompressionMaxBytes()), false, "压缩最大字节数");
        }
        systemConfigurationService.refreshCache();
    }

    // ── Compression config (used by upload service) ────

    public CompressionConfig getCompressionConfig() {
        return new CompressionConfig(
                getBool("image.compression.enabled", true),
                getInt("image.compression.max_width", 1920),
                getInt("image.compression.max_height", 1920),
                getDouble("image.compression.quality", 0.85),
                getInt("image.compression.max_bytes", 500_000)
        );
    }

    public String getLocalBaseUrl() {
        return getOrDefault("image.storage.local.base_url", "");
    }

    public String getDashscopeModel() {
        return getOrDefault("image.storage.dashscope.model", "qwen-vl-plus");
    }

    public String getDashscopeApiKey() {
        return getOrDefault("APP_AI_API_KEY", "");
    }

    // ── helpers ─────────────────────────────────────────

    private String getOrDefault(String key, String defaultValue) {
        String v = systemConfigurationService.getConfig(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private boolean getBool(String key, boolean defaultValue) {
        String v = systemConfigurationService.getConfig(key);
        if (v == null || v.isBlank()) return defaultValue;
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    private int getInt(String key, int defaultValue) {
        String v = systemConfigurationService.getConfig(key);
        if (v == null || v.isBlank()) return defaultValue;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private double getDouble(String key, double defaultValue) {
        String v = systemConfigurationService.getConfig(key);
        if (v == null || v.isBlank()) return defaultValue;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private void saveIfPresent(String key, String value, boolean encrypt) {
        if (value != null) {
            systemConfigurationService.saveConfig(key, value, encrypt, null);
        }
    }

    private static String maskSecret(String value) {
        if (value == null || value.length() <= 6) return value == null ? "" : "***";
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    // ── DTO records ─────────────────────────────────────

    public record CompressionConfig(boolean enabled, int maxWidth, int maxHeight, double quality, int maxBytes) {}

    @lombok.Data
    public static class ImageStorageConfig {
        private String mode;
        private String localBaseUrl;
        private String dashscopeModel;
        private String ossEndpoint;
        private String ossBucket;
        private String ossAccessKeyId;
        private String ossAccessKeySecret;
        private String ossRegion;
        private Boolean compressionEnabled;
        private Integer compressionMaxWidth;
        private Integer compressionMaxHeight;
        private Double compressionQuality;
        private Integer compressionMaxBytes;
    }
}
