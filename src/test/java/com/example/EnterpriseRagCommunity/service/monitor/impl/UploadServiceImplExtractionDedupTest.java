package com.example.EnterpriseRagCommunity.service.monitor.impl;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionService;
import com.example.EnterpriseRagCommunity.service.monitor.UploadFormatsConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UploadServiceImplExtractionDedupTest {
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void upload_whenDedupHit_shouldRequestExtraction() {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UploadFormatsConfigService uploadFormatsConfigService = mock(UploadFormatsConfigService.class);
        FileAssetExtractionService fileAssetExtractionService = mock(FileAssetExtractionService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));

        UploadFormatsConfigDTO cfg = UploadFormatsConfigDTO.empty();
        cfg.setEnabled(true);
        cfg.setMaxFilesPerRequest(10);
        cfg.setMaxFileSizeBytes(50L * 1024 * 1024);
        cfg.setMaxTotalSizeBytes(200L * 1024 * 1024);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfg);

        UploadFormatsConfigDTO.UploadFormatRuleDTO rule = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        rule.setFormat("TXT");
        rule.setEnabled(true);
        rule.setExtensions(java.util.List.of("txt"));
        rule.setParseEnabled(true);
        when(uploadFormatsConfigService.enabledExtensionToRule()).thenReturn(Map.of("txt", rule));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", java.util.List.of()));

        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", bytes);

        String sha256 = sha256Hex(bytes);

        FileAssetsEntity existing = new FileAssetsEntity();
        existing.setId(9L);
        existing.setUrl("/uploads/x");
        existing.setOriginalName("a.txt");
        existing.setSizeBytes((long) bytes.length);
        existing.setMimeType("text/plain");
        existing.setSha256(sha256);
        when(fileAssetsRepository.findBySha256(eq(sha256))).thenReturn(Optional.of(existing));

        ObjectMapper objectMapper = new ObjectMapper();
        UploadServiceImpl svc = new UploadServiceImpl(
                fileAssetsRepository,
                administratorService,
                uploadFormatsConfigService,
                fileAssetExtractionService,
                objectMapper
        );
        ReflectionTestUtils.setField(svc, "uploadRoot", "uploads");
        ReflectionTestUtils.setField(svc, "urlPrefix", "/uploads");

        UploadResultDTO out = svc.upload(file);
        assertEquals(9L, out.getId());

        verify(fileAssetExtractionService).requestExtractionIfEnabled(existing);
        verify(fileAssetsRepository, never()).save(any());
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
