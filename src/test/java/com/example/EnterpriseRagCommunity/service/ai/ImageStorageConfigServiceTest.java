package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageStorageConfigServiceTest {

    @Test
    void getMode_shouldSaveDefault_whenMissingOrBlank() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);

        when(scs.getConfig("image.storage.mode")).thenReturn(null, "   ");

        assertEquals(ImageStorageConfigService.StorageMode.DASHSCOPE_TEMP, service.getMode());
        assertEquals(ImageStorageConfigService.StorageMode.DASHSCOPE_TEMP, service.getMode());
        verify(scs, times(2)).saveConfig(
                "image.storage.mode",
                "DASHSCOPE_TEMP",
                false,
                "图片存储模式"
        );
    }

    @Test
    void getMode_shouldParseValidAndFallbackInvalid() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);

        when(scs.getConfig("image.storage.mode")).thenReturn(" local ", "unknown");

        assertEquals(ImageStorageConfigService.StorageMode.LOCAL, service.getMode());
        assertEquals(ImageStorageConfigService.StorageMode.DASHSCOPE_TEMP, service.getMode());
        verify(scs, never()).saveConfig(
                eq("image.storage.mode"),
                anyString(),
                anyBoolean(),
                anyString()
        );
    }

    @Test
    void getConfig_shouldApplyDefaultsAndMaskSecret() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);

        when(scs.getConfig("image.storage.mode")).thenReturn("aliyun_oss");
        when(scs.getConfig("image.storage.local.base_url")).thenReturn(" ");
        when(scs.getConfig("image.storage.dashscope.model")).thenReturn(null);
        when(scs.getConfig("image.storage.oss.endpoint")).thenReturn("oss-cn-hangzhou.aliyuncs.com");
        when(scs.getConfig("image.storage.oss.bucket")).thenReturn("bucket-a");
        when(scs.getConfig("image.storage.oss.access_key_id")).thenReturn("id-a");
        when(scs.getConfig("image.storage.oss.access_key_secret")).thenReturn("abcdef123456");
        when(scs.getConfig("image.storage.oss.region")).thenReturn("cn-hangzhou");
        when(scs.getConfig("image.compression.enabled")).thenReturn("1");
        when(scs.getConfig("image.compression.max_width")).thenReturn("x");
        when(scs.getConfig("image.compression.max_height")).thenReturn("1280");
        when(scs.getConfig("image.compression.quality")).thenReturn("bad");
        when(scs.getConfig("image.compression.max_bytes")).thenReturn("300000");

        ImageStorageConfigService.ImageStorageConfig cfg = service.getConfig();

        assertEquals("ALIYUN_OSS", cfg.getMode());
        assertEquals("", cfg.getLocalBaseUrl());
        assertEquals("qwen-vl-plus", cfg.getDashscopeModel());
        assertEquals("oss-cn-hangzhou.aliyuncs.com", cfg.getOssEndpoint());
        assertEquals("bucket-a", cfg.getOssBucket());
        assertEquals("id-a", cfg.getOssAccessKeyId());
        assertEquals("abc***456", cfg.getOssAccessKeySecret());
        assertEquals("cn-hangzhou", cfg.getOssRegion());
        assertTrue(cfg.getCompressionEnabled());
        assertEquals(1920, cfg.getCompressionMaxWidth());
        assertEquals(1280, cfg.getCompressionMaxHeight());
        assertEquals(0.85, cfg.getCompressionQuality());
        assertEquals(300000, cfg.getCompressionMaxBytes());
    }

    @Test
    void getConfig_shouldMaskShortOrMissingSecret() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);

        when(scs.getConfig("image.storage.mode")).thenReturn("LOCAL", "LOCAL");
        when(scs.getConfig("image.storage.oss.access_key_secret")).thenReturn("abc", (String) null);

        assertEquals("***", service.getConfig().getOssAccessKeySecret());
        assertEquals("***", service.getConfig().getOssAccessKeySecret());
    }

    @Test
    void getConfigRaw_shouldReturnUnmaskedSecret() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);

        when(scs.getConfig("image.storage.mode")).thenReturn("DASHSCOPE_TEMP");
        when(scs.getConfig("image.storage.oss.access_key_secret")).thenReturn("secret123");

        ImageStorageConfigService.ImageStorageConfig cfg = service.getConfigRaw();

        assertEquals("secret123", cfg.getOssAccessKeySecret());
    }

    @Test
    void updateConfig_shouldSaveAllPresentFields_andSkipMaskedSecret() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);
        ImageStorageConfigService.ImageStorageConfig payload = new ImageStorageConfigService.ImageStorageConfig();
        payload.setMode(" local ");
        payload.setLocalBaseUrl("https://cdn.example.com");
        payload.setDashscopeModel("qwen-vl-max");
        payload.setOssEndpoint("oss-cn-hz.aliyuncs.com");
        payload.setOssBucket("bkt");
        payload.setOssAccessKeyId("ak");
        payload.setOssAccessKeySecret("abc***xyz");
        payload.setOssRegion("cn-hz");
        payload.setCompressionEnabled(false);
        payload.setCompressionMaxWidth(1000);
        payload.setCompressionMaxHeight(900);
        payload.setCompressionQuality(0.7);
        payload.setCompressionMaxBytes(123456);

        service.updateConfig(payload);

        verify(scs).saveConfig("image.storage.mode", "LOCAL", false, "图片存储模式");
        verify(scs).saveConfig("image.storage.local.base_url", "https://cdn.example.com", false, null);
        verify(scs).saveConfig("image.storage.dashscope.model", "qwen-vl-max", false, null);
        verify(scs).saveConfig("image.storage.oss.endpoint", "oss-cn-hz.aliyuncs.com", true, null);
        verify(scs).saveConfig("image.storage.oss.bucket", "bkt", false, null);
        verify(scs).saveConfig("image.storage.oss.access_key_id", "ak", true, null);
        verify(scs, never()).saveConfig(eq("image.storage.oss.access_key_secret"), anyString(), eq(true), anyString());
        verify(scs).saveConfig("image.storage.oss.region", "cn-hz", false, null);
        verify(scs).saveConfig("image.compression.enabled", "false", false, "图片压缩开关");
        verify(scs).saveConfig("image.compression.max_width", "1000", false, "压缩最大宽度");
        verify(scs).saveConfig("image.compression.max_height", "900", false, "压缩最大高度");
        verify(scs).saveConfig("image.compression.quality", "0.7", false, "JPEG 压缩质量");
        verify(scs).saveConfig("image.compression.max_bytes", "123456", false, "压缩最大字节数");
        verify(scs).refreshCache();
    }

    @Test
    void updateConfig_shouldSaveRawSecret_andSkipNullFields() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);
        ImageStorageConfigService.ImageStorageConfig payload = new ImageStorageConfigService.ImageStorageConfig();
        payload.setMode(null);
        payload.setOssAccessKeySecret("raw-secret");

        service.updateConfig(payload);

        verify(scs, never()).saveConfig(eq("image.storage.mode"), anyString(), eq(false), anyString());
        verify(scs).saveConfig("image.storage.oss.access_key_secret", "raw-secret", true, "OSS AccessKey Secret");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(scs, times(1)).saveConfig(keyCaptor.capture(), eq("raw-secret"), eq(true), eq("OSS AccessKey Secret"));
        assertEquals("image.storage.oss.access_key_secret", keyCaptor.getValue());
        verify(scs).refreshCache();
    }

    @Test
    void updateConfig_shouldSkipSecretSave_whenSecretNull() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);
        ImageStorageConfigService.ImageStorageConfig payload = new ImageStorageConfigService.ImageStorageConfig();
        payload.setMode("aliyun_oss");
        payload.setOssAccessKeySecret(null);

        service.updateConfig(payload);

        verify(scs).saveConfig("image.storage.mode", "ALIYUN_OSS", false, "图片存储模式");
        verify(scs, never()).saveConfig(eq("image.storage.oss.access_key_secret"), anyString(), eq(true), anyString());
        verify(scs).refreshCache();
    }

    @Test
    void getCompressionAndSimpleGetters_shouldUseConfiguredAndFallbackValues() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);

        when(scs.getConfig("image.compression.enabled")).thenReturn("False");
        when(scs.getConfig("image.compression.max_width")).thenReturn("1024");
        when(scs.getConfig("image.compression.max_height")).thenReturn(" ");
        when(scs.getConfig("image.compression.quality")).thenReturn("0.66");
        when(scs.getConfig("image.compression.max_bytes")).thenReturn("oops");
        when(scs.getConfig("image.storage.local.base_url")).thenReturn("https://assets.example.com");
        when(scs.getConfig("image.storage.dashscope.model")).thenReturn(" ");
        when(scs.getConfig("APP_AI_API_KEY")).thenReturn("k");

        ImageStorageConfigService.CompressionConfig compression = service.getCompressionConfig();

        assertFalse(compression.enabled());
        assertEquals(1024, compression.maxWidth());
        assertEquals(1920, compression.maxHeight());
        assertEquals(0.66, compression.quality());
        assertEquals(500000, compression.maxBytes());
        assertEquals("https://assets.example.com", service.getLocalBaseUrl());
        assertEquals("qwen-vl-plus", service.getDashscopeModel());
        assertEquals("k", service.getDashscopeApiKey());
    }

    @Test
    void getConfig_shouldFallbackWhenCompressionEnabledInvalid() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);

        when(scs.getConfig("image.storage.mode")).thenReturn("LOCAL");
        when(scs.getConfig("image.compression.enabled")).thenReturn("oops");

        ImageStorageConfigService.ImageStorageConfig cfg = service.getConfig();

        assertFalse(cfg.getCompressionEnabled());
    }

    @Test
    void getters_shouldFallbackToDefaultsWhenValuesMissing() {
        SystemConfigurationService scs = mock(SystemConfigurationService.class);
        ImageStorageConfigService service = new ImageStorageConfigService(scs);

        when(scs.getConfig("image.storage.mode")).thenReturn("LOCAL");
        when(scs.getConfig("image.storage.local.base_url")).thenReturn(null);
        when(scs.getConfig("image.storage.dashscope.model")).thenReturn(" ");
        when(scs.getConfig("APP_AI_API_KEY")).thenReturn(" ");
        when(scs.getConfig("image.compression.enabled")).thenReturn(" ");
        when(scs.getConfig("image.compression.max_width")).thenReturn(null);
        when(scs.getConfig("image.compression.max_height")).thenReturn(null);
        when(scs.getConfig("image.compression.quality")).thenReturn(null);
        when(scs.getConfig("image.compression.max_bytes")).thenReturn(null);
        when(scs.getConfig("image.storage.oss.access_key_secret")).thenReturn(null);

        ImageStorageConfigService.ImageStorageConfig cfg = service.getConfig();
        ImageStorageConfigService.CompressionConfig compression = service.getCompressionConfig();

        assertEquals("", service.getLocalBaseUrl());
        assertEquals("qwen-vl-plus", service.getDashscopeModel());
        assertEquals("", service.getDashscopeApiKey());
        assertTrue(compression.enabled());
        assertEquals(1920, compression.maxWidth());
        assertEquals(1920, compression.maxHeight());
        assertEquals(0.85, compression.quality());
        assertEquals(500000, compression.maxBytes());
        assertEquals("***", cfg.getOssAccessKeySecret());
    }
}
