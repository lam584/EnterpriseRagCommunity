package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.ImageUploadLogEntity;
import com.example.EnterpriseRagCommunity.repository.ai.ImageUploadLogRepository;
import com.example.EnterpriseRagCommunity.service.ai.ImageStorageConfigService.CompressionConfig;
import com.example.EnterpriseRagCommunity.service.ai.ImageStorageConfigService.ImageStorageConfig;
import com.example.EnterpriseRagCommunity.service.ai.ImageStorageConfigService.StorageMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmImageUploadServiceTest {

    @BeforeAll
    static void installHttpsStub() {
        HttpsStub.install();
    }

    @Test
    void resolveImageUrl_shouldReturnNull_whenLocalPathNullOrBlank() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        assertNull(svc.resolveImageUrl(null, "image/png", "m"));
        assertNull(svc.resolveImageUrl("", "image/png", "m"));
        assertNull(svc.resolveImageUrl("   ", "image/png", "m"));
    }

    @Test
    void resolveImageUrl_localMode_shouldReturnLocalPath_whenBaseUrlBlank() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.LOCAL);
        when(configService.getLocalBaseUrl()).thenReturn("   ");
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        assertEquals("/uploads/a.png", svc.resolveImageUrl("/uploads/a.png", "image/png", null));
    }

    @Test
    void resolveImageUrl_localMode_shouldNormalizeBaseUrlAndUploadsPrefix() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.LOCAL);
        when(configService.getLocalBaseUrl()).thenReturn("https://cdn.example.com/");
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        assertEquals("https://cdn.example.com/uploads/2026/01/a.png",
                svc.resolveImageUrl("2026/01/a.png", "image/png", null));
        assertEquals("https://cdn.example.com/uploads/2026/01/a.png",
                svc.resolveImageUrl("/2026/01/a.png", "image/png", null));
        assertEquals("https://cdn.example.com/uploads/2026/01/a.png",
                svc.resolveImageUrl("/uploads/2026/01/a.png", "image/png", null));
    }

    @Test
    void resolveImageUrl_localMode_shouldHandleBaseUrlWithoutTrailingSlash() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.LOCAL);
        when(configService.getLocalBaseUrl()).thenReturn("https://cdn.example.com");
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        assertEquals("https://cdn.example.com/uploads/a.png",
                svc.resolveImageUrl("/uploads/a.png", "image/png", null));
    }

    @Test
    void resolveImageUrl_localMode_shouldKeepAbsoluteHttpUrl() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.LOCAL);
        when(configService.getLocalBaseUrl()).thenReturn("https://cdn.example.com");
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        assertEquals("https://img.example.com/a.png",
                svc.resolveImageUrl("https://img.example.com/a.png", "image/png", null));
        assertEquals("http://img.example.com/a.png",
                svc.resolveImageUrl("http://img.example.com/a.png", "image/png", null));
    }

    @Test
    void resolveImageUrl_localMode_shouldReturnLocalPath_whenBaseUrlNull() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.LOCAL);
        when(configService.getLocalBaseUrl()).thenReturn(null);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        assertEquals("/uploads/a.png", svc.resolveImageUrl("/uploads/a.png", "image/png", null));
    }

    @Test
    void resolveImageUrl_shouldReturnNull_whenModeNull() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(null);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        assertNull(svc.resolveImageUrl("/uploads/a.png", "image/png", "m"));
    }

    @Test
    void isDashscopeMode_shouldReflectMode() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        assertTrue(svc.isDashscopeMode());

        when(configService.getMode()).thenReturn(StorageMode.LOCAL);
        assertFalse(svc.isDashscopeMode());
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnCachedRemoteUrl() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");

        ImageUploadLogEntity e = new ImageUploadLogEntity();
        e.setRemoteUrl("oss://cached/k.png");
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                eq("/uploads/a.png"), eq("m1"), eq("ACTIVE"), any(LocalDateTime.class)
        )).thenReturn(Optional.of(e));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertEquals("oss://cached/k.png", svc.resolveImageUrl("/uploads/a.png", "image/png", null));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenFileMissing(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.resolve("uploads").toString());

        assertNull(svc.resolveImageUrl("/uploads/nope.png", "image/png", null));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenApiKeyBlank(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn(" ");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 1)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenGetPolicyHttpError(@TempDir Path dir) throws Exception {
        HttpsStub.policyResponseCode = 500;
        HttpsStub.policyErrorBody = "err";
        HttpsStub.policyBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 2)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenGetPolicyMissingData(@TempDir Path dir) throws Exception {
        HttpsStub.policyResponseCode = 200;
        HttpsStub.policyBody = "{\"foo\":1}";
        HttpsStub.policyErrorBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 3)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenUploadHostBlankInPolicy(@TempDir Path dir) throws Exception {
        HttpsStub.policyResponseCode = 200;
        HttpsStub.policyBody = "{\"data\":{\"upload_host\":\" \",\"upload_dir\":\"dir\",\"oss_access_key_id\":\"id\",\"signature\":\"sig\",\"policy\":\"p\",\"x_oss_object_acl\":\"a\",\"x_oss_forbid_overwrite\":\"true\"}}";
        HttpsStub.policyErrorBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 4)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
        verify(repo, never()).save(any());
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenUploadHostMissingInPolicy(@TempDir Path dir) throws Exception {
        HttpsStub.policyResponseCode = 200;
        HttpsStub.policyBody = "{\"data\":{\"upload_dir\":\"dir\",\"oss_access_key_id\":\"id\",\"signature\":\"sig\",\"policy\":\"p\",\"x_oss_object_acl\":\"a\",\"x_oss_forbid_overwrite\":\"true\"}}";
        HttpsStub.policyErrorBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 22)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldUseConfigModel_whenModelNameBlank(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("cfgModel");
        when(configService.getDashscopeApiKey()).thenReturn(" ");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 23)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "   "));

        verify(repo).findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                eq(f.toString()), eq("cfgModel"), eq("ACTIVE"), any(LocalDateTime.class)
        );
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenApiKeyNull(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn(null);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 24)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenFileEmpty(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, new byte[0]);

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldUploadAndLog_whenPolicyAndUploadOk(@TempDir Path dir) throws Exception {
        HttpsStub.policyResponseCode = 200;
        HttpsStub.policyBody = "{\"data\":{\"upload_host\":\"https://upload.test\",\"upload_dir\":\"dir\",\"oss_access_key_id\":\"id\",\"signature\":\"sig\",\"policy\":\"p\",\"x_oss_object_acl\":\"a\",\"x_oss_forbid_overwrite\":\"true\"}}";
        HttpsStub.policyErrorBody = null;
        HttpsStub.uploadResponseCode = 200;
        HttpsStub.uploadErrorBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 5)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        String url = svc.resolveImageUrl(f.toString(), "image/png", "m1");
        assertTrue(url.startsWith("oss://dir/"));

        ArgumentCaptor<ImageUploadLogEntity> captor = ArgumentCaptor.forClass(ImageUploadLogEntity.class);
        verify(repo).save(captor.capture());
        assertEquals("DASHSCOPE_TEMP", captor.getValue().getStorageMode());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertEquals(url, captor.getValue().getRemoteUrl());
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldReturnNull_whenUploadFails(@TempDir Path dir) throws Exception {
        HttpsStub.policyResponseCode = 200;
        HttpsStub.policyBody = "{\"data\":{\"upload_host\":\"https://upload.test\",\"upload_dir\":\"dir\",\"oss_access_key_id\":\"id\",\"signature\":\"sig\",\"policy\":\"p\",\"x_oss_object_acl\":\"a\",\"x_oss_forbid_overwrite\":\"true\"}}";
        HttpsStub.policyErrorBody = null;
        HttpsStub.uploadResponseCode = 500;
        HttpsStub.uploadErrorBody = "oops";

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 12)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
    }

    @Test
    void resolveImageUrl_dashscopeMode_shouldAccept204AsSuccess(@TempDir Path dir) throws Exception {
        HttpsStub.policyResponseCode = 200;
        HttpsStub.policyBody = "{\"data\":{\"upload_host\":\"https://upload.test\",\"upload_dir\":\"dir\",\"oss_access_key_id\":\"id\",\"signature\":\"sig\",\"policy\":\"p\",\"x_oss_object_acl\":\"a\",\"x_oss_forbid_overwrite\":\"true\"}}";
        HttpsStub.policyErrorBody = null;
        HttpsStub.uploadResponseCode = 204;
        HttpsStub.uploadErrorBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 13)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        String url = svc.resolveImageUrl(f.toString(), "image/png", "m1");
        assertTrue(url.startsWith("oss://dir/"));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnCachedRemoteUrl() {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);

        ImageUploadLogEntity e = new ImageUploadLogEntity();
        e.setRemoteUrl("https://cached/obj");
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                eq("/uploads/a.png"), eq("ALIYUN_OSS"), eq("ACTIVE")
        )).thenReturn(Optional.of(e));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertEquals("https://cached/obj", svc.resolveImageUrl("/uploads/a.png", "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenFileMissing(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.resolve("uploads").toString());

        assertNull(svc.resolveImageUrl("/uploads/nope.jpg", "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenConfigIncomplete(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint(" ");
        cfg.setOssBucket("b");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 6)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenEndpointNull(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint(null);
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 25)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenFileEmpty(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.jpg");
        Files.write(f, new byte[0]);

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenBucketNull(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket(null);
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 35)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenAccessKeyIdNull(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId(null);
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 36)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenBucketMissing(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket(" ");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 14)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenAccessKeyIdMissing(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId(" ");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 15)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenAccessKeySecretMissing(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 16)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenAccessKeySecretNull(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret(null);
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 40)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldPutAndLog_whenPutOk(@TempDir Path dir) throws Exception {
        HttpsStub.ossPutResponseCode = 200;
        HttpsStub.ossPutErrorBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 7)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        String url = svc.resolveImageUrl(f.toString(), null, null);
        assertNotNull(url);
        assertTrue(url.startsWith("https://bucket.oss.test/llm-images/"));
        verify(repo).save(any(ImageUploadLogEntity.class));
        assertTrue(HttpsStub.lastOssPutHeaders.containsKey("Content-Type"));
        assertEquals("application/octet-stream", HttpsStub.lastOssPutHeaders.get("Content-Type"));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldAccept201AsSuccess(@TempDir Path dir) throws Exception {
        HttpsStub.ossPutResponseCode = 201;
        HttpsStub.ossPutErrorBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 17)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        String url = svc.resolveImageUrl(f.toString(), "image/jpeg", null);
        assertNotNull(url);
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldPreserveContentType_whenProvided(@TempDir Path dir) throws Exception {
        HttpsStub.ossPutResponseCode = 200;
        HttpsStub.ossPutErrorBody = null;
        HttpsStub.lastOssPutHeaders = new HashMap<>();

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 18)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNotNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
        assertEquals("image/jpeg", HttpsStub.lastOssPutHeaders.get("Content-Type"));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldDefaultContentType_whenBlank(@TempDir Path dir) throws Exception {
        HttpsStub.ossPutResponseCode = 200;
        HttpsStub.ossPutErrorBody = null;
        HttpsStub.lastOssPutHeaders = new HashMap<>();

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 26)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNotNull(svc.resolveImageUrl(f.toString(), " ", null));
        assertEquals("application/octet-stream", HttpsStub.lastOssPutHeaders.get("Content-Type"));
    }

    @Test
    void resolveImageUrl_aliyunOssMode_shouldReturnNull_whenPutFails(@TempDir Path dir) throws Exception {
        HttpsStub.ossPutResponseCode = 403;
        HttpsStub.ossPutErrorBody = "denied";

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.ALIYUN_OSS);
        when(repo.findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
                anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));

        ImageStorageConfig cfg = new ImageStorageConfig();
        cfg.setOssEndpoint("oss.test");
        cfg.setOssBucket("bucket");
        cfg.setOssAccessKeyId("id");
        cfg.setOssAccessKeySecret("sec");
        when(configService.getConfigRaw()).thenReturn(cfg);

        Path f = dir.resolve("a.jpg");
        Files.write(f, encodeJpeg(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 8)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/jpeg", null));
    }

    @Test
    void testCompress_shouldThrow_whenFileUnreadable(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 100, 100, 0.8, 10_000));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.resolve("uploads").toString());

        assertThrows(IllegalArgumentException.class, () -> svc.testCompress("/uploads/nope.png"));
    }

    @Test
    void testCompress_shouldThrow_whenNotAnImage(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 100, 100, 0.8, 10_000));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.toString());

        Path f = dir.resolve("x.png");
        Files.write(f, "nope".getBytes());

        assertThrows(IllegalArgumentException.class, () -> svc.testCompress(f.toString()));
    }

    @Test
    void testCompress_shouldReturnCompressedPreview_whenOverMaxBytes(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 200, 200, 0.9, 10_000));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.toString());

        BufferedImage big = noiseImage(1000, 600, BufferedImage.TYPE_INT_ARGB, 9);
        byte[] src = encodePng(big);
        Path f = dir.resolve("big.png");
        Files.write(f, src);

        Map<String, Object> r = svc.testCompress(f.toString());
        assertNotNull(r.get("originalSize"));
        assertNotNull(r.get("compressedSize"));
        assertTrue((Boolean) r.get("wasCompressed"));
        assertTrue(((String) r.get("originalBase64")).startsWith("data:image/png;base64,"));
        assertTrue(((String) r.get("compressedBase64")).startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void testCompress_shouldThrow_whenFileEmpty(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 100, 100, 0.8, 10_000));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.toString());

        Path f = dir.resolve("x.png");
        Files.write(f, new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> svc.testCompress(f.toString()));
    }

    @Test
    void testCompress_shouldReturnWasCompressedFalse_whenUnderLimit(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 5000, 5000, 0.9, 5_000_000));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.toString());

        BufferedImage img = noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 27);
        byte[] src = encodePng(img);
        Path f = dir.resolve("small.png");
        Files.write(f, src);

        Map<String, Object> r = svc.testCompress(f.toString());
        assertFalse((Boolean) r.get("wasCompressed"));
        assertEquals(r.get("originalSize"), r.get("compressedSize"));
        assertEquals(r.get("originalBase64"), r.get("compressedBase64"));
    }

    @Test
    void testCompress_shouldHandleCompressedImageNull_whenReadReturnsNull(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 200, 200, 0.9, 20_000));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.toString());

        BufferedImage img = noiseImage(1200, 800, BufferedImage.TYPE_INT_ARGB, 28);
        byte[] src = encodePng(img);
        Path f = dir.resolve("big.png");
        Files.write(f, src);

        try (MockedStatic<ImageIO> mocked = Mockito.mockStatic(ImageIO.class, Mockito.CALLS_REAL_METHODS)) {
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger(0);
            mocked.when(() -> ImageIO.read(any(ByteArrayInputStream.class))).thenAnswer(inv -> {
                int c = calls.incrementAndGet();
                if (c == 3) return null;
                return Mockito.CALLS_REAL_METHODS.answer(inv);
            });
            Map<String, Object> r = svc.testCompress(f.toString());
            assertTrue((Boolean) r.get("wasCompressed"));
            assertEquals(r.get("originalBase64"), r.get("compressedBase64"));
        }
    }

    @Test
    void readLocalFile_shouldCoverMoreBranches(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, dir.toString());

        Method readLocalFile = LlmImageUploadService.class.getDeclaredMethod("readLocalFile", String.class);
        readLocalFile.setAccessible(true);

        assertNull(readLocalFile.invoke(svc, dir.resolve("missing.png").toString()));
        assertNull(readLocalFile.invoke(svc, dir.toString()));

        Path f1 = dir.resolve("a.png");
        Files.write(f1, encodePng(noiseImage(32, 32, BufferedImage.TYPE_INT_RGB, 29)));
        assertNotNull(readLocalFile.invoke(svc, "a.png"));

        Path uploadsRoot = dir.resolve("uploads");
        String relPath = "uploads/junit-" + java.util.UUID.randomUUID() + "/u.png";
        Path f2 = uploadsRoot.resolve(relPath.substring("uploads/".length()));
        Files.createDirectories(f2.getParent());
        Files.write(f2, encodePng(noiseImage(32, 32, BufferedImage.TYPE_INT_RGB, 30)));
        setUploadRoot(svc, uploadsRoot.toString());
        assertNotNull(readLocalFile.invoke(svc, relPath));

        Path d = uploadsRoot.resolve("dir");
        Files.createDirectories(d);
        assertNull(readLocalFile.invoke(svc, "dir"));
    }

    @Test
    void compressIfEnabled_shouldCoverRemainingBranches() throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        Method compress = LlmImageUploadService.class.getDeclaredMethod("compressIfEnabled", byte[].class, String.class);
        compress.setAccessible(true);

        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 1000, 1000, 0.9, 1));
        assertNull(compress.invoke(svc, null, "image/png"));

        byte[] src = encodePng(noiseImage(500, 500, BufferedImage.TYPE_INT_RGB, 31));
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 1000, 1000, 0.9, 200_000));
        assertNotNull(compress.invoke(svc, src, null));

        try (MockedStatic<ImageIO> mocked = Mockito.mockStatic(ImageIO.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ImageIO.read(any(ByteArrayInputStream.class))).thenReturn(noiseImage(500, 100, BufferedImage.TYPE_INT_RGB, 32));
            when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 200, 1000, 0.9, 1));
            assertNotNull(compress.invoke(svc, src, "image/png"));
        }

        try (MockedStatic<ImageIO> mocked = Mockito.mockStatic(ImageIO.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ImageIO.read(any(ByteArrayInputStream.class))).thenThrow(new IOException("x"));
            when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 1000, 1000, 0.9, 1));
            assertEquals(src, compress.invoke(svc, src, "image/png"));
        }
    }

    @Test
    void compressIfEnabled_shouldResizeWhenHeightExceedsMax() throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        Method compress = LlmImageUploadService.class.getDeclaredMethod("compressIfEnabled", byte[].class, String.class);
        compress.setAccessible(true);

        byte[] src = encodePng(noiseImage(500, 500, BufferedImage.TYPE_INT_RGB, 37));

        try (MockedStatic<ImageIO> mocked = Mockito.mockStatic(ImageIO.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ImageIO.read(any(ByteArrayInputStream.class))).thenReturn(noiseImage(100, 500, BufferedImage.TYPE_INT_RGB, 38));
            when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 200, 200, 0.9, 1));
            assertNotNull(compress.invoke(svc, src, "image/png"));
        }
    }

    @Test
    void readLocalFile_shouldResolveUploadsRelativePath(@TempDir Path dir) throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn(" ");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path uploadRoot = dir.resolve("uploads");
        Path f = uploadRoot.resolve("2026/01/a.png");
        Files.createDirectories(f.getParent());
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 10)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, uploadRoot.toString());

        assertNull(svc.resolveImageUrl("/uploads/2026/01/a.png", "image/png", "m1"));
    }

    @Test
    void compressIfEnabled_shouldReturnSrc_forCommonEarlyReturns() throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 100, 100, 0.8, 1000000));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        Method compress = LlmImageUploadService.class.getDeclaredMethod("compressIfEnabled", byte[].class, String.class);
        compress.setAccessible(true);

        byte[] small = encodePng(noiseImage(16, 16, BufferedImage.TYPE_INT_RGB, 19));
        assertEquals(small, compress.invoke(svc, small, "application/pdf"));
        byte[] empty = new byte[0];
        assertEquals(empty, compress.invoke(svc, empty, "image/png"));

        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 1));
        assertEquals(small, compress.invoke(svc, small, "image/png"));
    }

    @Test
    void compressIfEnabled_shouldReturnSrc_whenImageReadNull() throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 100, 100, 0.8, 1));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        Method compress = LlmImageUploadService.class.getDeclaredMethod("compressIfEnabled", byte[].class, String.class);
        compress.setAccessible(true);

        byte[] src = "not image".getBytes();
        assertEquals(src, compress.invoke(svc, src, "image/png"));
    }

    @Test
    void compressIfEnabled_shouldReturnSrc_whenNoJpegWriterAvailable() throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(true, 100, 100, 0.8, 1));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        Method compress = LlmImageUploadService.class.getDeclaredMethod("compressIfEnabled", byte[].class, String.class);
        compress.setAccessible(true);

        byte[] src = encodePng(noiseImage(200, 200, BufferedImage.TYPE_INT_RGB, 20));
        try (MockedStatic<ImageIO> mocked = Mockito.mockStatic(ImageIO.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ImageIO.getImageWritersByFormatName("jpeg")).thenReturn(Collections.emptyIterator());
            assertEquals(src, compress.invoke(svc, src, "image/png"));
        }
    }

    @Test
    void readLocalFile_shouldHandleInvalidUploadRoot() throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        setUploadRoot(svc, "\u0000");

        Method readLocalFile = LlmImageUploadService.class.getDeclaredMethod("readLocalFile", String.class);
        readLocalFile.setAccessible(true);
        assertNull(readLocalFile.invoke(svc, "/uploads/a.png"));
    }

    @Test
    void helperMethods_shouldCoverNullBranchesViaReflection() throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        Method extractFileName = LlmImageUploadService.class.getDeclaredMethod("extractFileName", String.class);
        extractFileName.setAccessible(true);
        assertEquals("image.bin", extractFileName.invoke(null, new Object[]{null}));
        assertEquals("a.png", extractFileName.invoke(null, "C:\\x\\a.png"));
        assertEquals("a.png", extractFileName.invoke(null, "a.png"));

        Method guessMime = LlmImageUploadService.class.getDeclaredMethod("guessImageMimeType", String.class);
        guessMime.setAccessible(true);
        assertEquals("image/jpeg", guessMime.invoke(null, new Object[]{null}));
        assertEquals("image/png", guessMime.invoke(null, "x.png"));
        assertEquals("image/gif", guessMime.invoke(null, "x.gif"));
        assertEquals("image/webp", guessMime.invoke(null, "x.webp"));
        assertEquals("image/bmp", guessMime.invoke(null, "x.bmp"));
        assertEquals("image/tiff", guessMime.invoke(null, "x.tif"));
        assertEquals("image/tiff", guessMime.invoke(null, "x.tiff"));
        assertEquals("image/jpeg", guessMime.invoke(null, "x.jpg"));

        Method toBase64 = LlmImageUploadService.class.getDeclaredMethod("toBase64Preview", BufferedImage.class, String.class, int.class);
        toBase64.setAccessible(true);
        assertEquals("", toBase64.invoke(null, null, "image/png", 10));
        BufferedImage small = noiseImage(20, 20, BufferedImage.TYPE_INT_RGB, 21);
        String jpg = (String) toBase64.invoke(null, small, "image/jpeg", 100);
        assertTrue(jpg.startsWith("data:image/jpeg;base64,"));
        assertFalse(jpg.isBlank());
        String png = (String) toBase64.invoke(null, small, "image/png", 100);
        assertTrue(png.startsWith("data:image/png;base64,"));
        BufferedImage big = noiseImage(1000, 600, BufferedImage.TYPE_INT_RGB, 33);
        String jpgResize = (String) toBase64.invoke(null, big, "image/jpeg", 200);
        assertTrue(jpgResize.startsWith("data:image/jpeg;base64,"));
        BufferedImage wide = noiseImage(1000, 100, BufferedImage.TYPE_INT_RGB, 39);
        String jpgWide = (String) toBase64.invoke(null, wide, "image/jpeg", 200);
        assertTrue(jpgWide.startsWith("data:image/jpeg;base64,"));
        String jpgNullMime = (String) toBase64.invoke(null, small, null, 100);
        assertTrue(jpgNullMime.startsWith("data:image/jpeg;base64,"));

        Method readStream = LlmImageUploadService.class.getDeclaredMethod("readStream", InputStream.class);
        readStream.setAccessible(true);
        assertEquals("", readStream.invoke(null, new Object[]{null}));
    }

    @Test
    void logUpload_shouldSwallowRepositoryErrors() throws Exception {
        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        doThrow(new RuntimeException("x")).when(repo).save(any(ImageUploadLogEntity.class));
        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);

        Method logUpload = LlmImageUploadService.class.getDeclaredMethod(
                "logUpload", String.class, String.class, String.class, String.class, long.class, int.class, LocalDateTime.class);
        logUpload.setAccessible(true);
        logUpload.invoke(svc, "/a", "r", "M", "model", 1L, 1, null);
    }

    @Test
    void getDashscopeUploadPolicy_shouldHandleErrorStreamReadFailure(@TempDir Path dir) throws Exception {
        HttpsStub.policyResponseCode = 500;
        HttpsStub.policyErrorBodyStream = new ThrowingInputStream();
        HttpsStub.policyErrorBody = null;
        HttpsStub.policyBody = null;

        ImageStorageConfigService configService = mock(ImageStorageConfigService.class);
        ImageUploadLogRepository repo = mock(ImageUploadLogRepository.class);
        when(configService.getMode()).thenReturn(StorageMode.DASHSCOPE_TEMP);
        when(configService.getDashscopeModel()).thenReturn("m1");
        when(configService.getDashscopeApiKey()).thenReturn("k");
        when(configService.getCompressionConfig()).thenReturn(new CompressionConfig(false, 100, 100, 0.8, 10_000));
        when(repo.findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        Path f = dir.resolve("a.png");
        Files.write(f, encodePng(noiseImage(64, 64, BufferedImage.TYPE_INT_RGB, 11)));

        LlmImageUploadService svc = new LlmImageUploadService(configService, repo);
        assertNull(svc.resolveImageUrl(f.toString(), "image/png", "m1"));
    }

    private static void setUploadRoot(LlmImageUploadService svc, String uploadRoot) throws Exception {
        Field f = LlmImageUploadService.class.getDeclaredField("uploadRoot");
        f.setAccessible(true);
        f.set(svc, uploadRoot);
    }

    private static BufferedImage noiseImage(int w, int h, int bufferedImageType, long seed) {
        BufferedImage bi = new BufferedImage(w, h, bufferedImageType);
        Random r = new Random(seed);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = (r.nextInt(256) << 16) | (r.nextInt(256) << 8) | r.nextInt(256);
                bi.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        return bi;
    }

    private static byte[] encodePng(BufferedImage bi) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bos);
        return bos.toByteArray();
    }

    private static byte[] encodeJpeg(BufferedImage bi) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "jpeg", bos);
        return bos.toByteArray();
    }

    static final class ThrowingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("x");
        }
    }

    static final class HttpsStub {
        private static final AtomicBoolean installed = new AtomicBoolean(false);

        static volatile int policyResponseCode = 200;
        static volatile String policyBody;
        static volatile String policyErrorBody;
        static volatile InputStream policyErrorBodyStream;

        static volatile int uploadResponseCode = 200;
        static volatile String uploadErrorBody;

        static volatile int ossPutResponseCode = 200;
        static volatile String ossPutErrorBody;
        static volatile Map<String, String> lastOssPutHeaders = new HashMap<>();

        static void install() {
            if (!installed.compareAndSet(false, true)) return;
            try {
                URL.setURLStreamHandlerFactory(new Factory());
            } catch (Error ignored) {
            }
        }

        static final class Factory implements URLStreamHandlerFactory {
            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                if (!"https".equalsIgnoreCase(protocol)) return null;
                return new Handler();
            }
        }

        static final class Handler extends URLStreamHandler {
            @Override
            protected URLConnection openConnection(URL u) {
                String host = u.getHost();
                if ("dashscope.aliyuncs.com".equalsIgnoreCase(host)) {
                    return new FakeHttpURLConnection(u, policyResponseCode, policyBody, policyErrorBody, policyErrorBodyStream);
                }
                if ("upload.test".equalsIgnoreCase(host)) {
                    return new FakeHttpURLConnection(u, uploadResponseCode, "", uploadErrorBody, null);
                }
                if ("bucket.oss.test".equalsIgnoreCase(host)) {
                    return new FakeHttpURLConnection(u, ossPutResponseCode, "", ossPutErrorBody, null) {
                        @Override
                        public void setRequestProperty(String key, String value) {
                            super.setRequestProperty(key, value);
                            lastOssPutHeaders.put(key, value);
                        }
                    };
                }
                return new FakeHttpURLConnection(u, 404, "", "not found", null);
            }
        }
    }

    static class FakeHttpURLConnection extends HttpURLConnection {
        private final int responseCode;
        private final String body;
        private final String errorBody;
        private final InputStream errorBodyStream;
        private final Map<String, String> headers = new HashMap<>();
        private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
        private String method;
        private boolean doOutput;

        protected FakeHttpURLConnection(URL u, int responseCode, String body, String errorBody, InputStream errorBodyStream) {
            super(u);
            this.responseCode = responseCode;
            this.body = body;
            this.errorBody = errorBody;
            this.errorBodyStream = errorBodyStream;
        }

        @Override
        public void setRequestMethod(String method) {
            this.method = method;
        }

        @Override
        public void setDoOutput(boolean dooutput) {
            this.doOutput = dooutput;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            headers.put(key, value);
        }

        @Override
        public OutputStream getOutputStream() {
            if (!doOutput) {
                throw new IllegalStateException("doOutput=false");
            }
            return requestBody;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(body == null ? new byte[0] : body.getBytes());
        }

        @Override
        public InputStream getErrorStream() {
            if (errorBodyStream != null) return errorBodyStream;
            if (errorBody == null) return null;
            return new ByteArrayInputStream(errorBody.getBytes());
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }
}
