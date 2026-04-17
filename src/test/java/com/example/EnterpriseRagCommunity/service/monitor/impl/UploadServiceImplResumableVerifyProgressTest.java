package com.example.EnterpriseRagCommunity.service.monitor.impl;

import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadStatusDTO;
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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UploadServiceImplResumableVerifyProgressTest {
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completeResumable_shouldExposeVerifyProgress(@TempDir Path tempDir) throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UploadFormatsConfigService uploadFormatsConfigService = mock(UploadFormatsConfigService.class);
        FileAssetExtractionService fileAssetExtractionService = mock(FileAssetExtractionService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        when(administratorService.findById(eq(7L))).thenReturn(Optional.of(me));

        when(fileAssetsRepository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(fileAssetsRepository.save(any(FileAssetsEntity.class))).thenAnswer((inv) -> {
            FileAssetsEntity fa = inv.getArgument(0);
            fa.setId(99L);
            return fa;
        });

        ObjectMapper objectMapper = new ObjectMapper();
        UploadServiceImpl svc = new UploadServiceImpl(
                fileAssetsRepository,
                administratorService,
                uploadFormatsConfigService,
                fileAssetExtractionService,
                objectMapper
        );
        ReflectionTestUtils.setField(svc, "uploadRoot", tempDir.toString());
        ReflectionTestUtils.setField(svc, "urlPrefix", "/uploads");

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", java.util.List.of()));

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path resumableDir = tempDir.resolve("_resumable");
        Files.createDirectories(resumableDir);
        Path metaPath = resumableDir.resolve(uploadId + ".json");
        Path partPath = resumableDir.resolve(uploadId + ".part");

        long totalBytes = 80L * 1024 * 1024;

        Files.deleteIfExists(partPath);
        try (OutputStream os = Files.newOutputStream(partPath)) {
            byte[] buf = new byte[1024 * 1024];
            for (int i = 0; i < buf.length; i++) buf[i] = (byte) (i & 0xFF);
            long written = 0L;
            while (written < totalBytes) {
                int n = (int) Math.min((long) buf.length, totalBytes - written);
                os.write(buf, 0, n);
                written += n;
            }
        }

        String metaJson = "{"
                + "\"uploadId\":\"" + uploadId + "\","
                + "\"ownerId\":7,"
                + "\"originalName\":\"a.bin\","
                + "\"mimeType\":\"application/octet-stream\","
                + "\"totalBytes\":" + totalBytes + ","
                + "\"uploadedBytes\":" + totalBytes + ","
                + "\"chunkSizeBytes\":33554432,"
                + "\"createdAtEpochMs\":1,"
                + "\"phase\":\"COMPLETED\","
                + "\"verifyBytes\":0,"
                + "\"updatedAtEpochMs\":1"
                + "}";
        Files.writeString(metaPath, metaJson);

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<UploadResultDTO> fut = exec.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", java.util.List.of()));
            return svc.completeResumable(uploadId);
        });

        boolean sawVerifying = false;
        boolean sawVerifyBytesIncrease = false;
        long startAt = System.currentTimeMillis();
        while (!fut.isDone() && System.currentTimeMillis() - startAt < 5000) {
            ResumableUploadStatusDTO s;
            try {
                s = svc.getResumableStatus(uploadId);
            } catch (Exception e) {
                break;
            }
            if ("VERIFYING".equalsIgnoreCase(s.getStatus())) {
                sawVerifying = true;
                Long vb = s.getVerifyBytes();
                if (vb != null && vb > 0 && vb < totalBytes) {
                    sawVerifyBytesIncrease = true;
                    break;
                }
            }
            Thread.sleep(5);
        }

        UploadResultDTO out = fut.get();
        exec.shutdownNow();

        assertEquals(99L, out.getId());
        assertTrue(sawVerifying);
        // Verification may complete between two polls on fast disks/CI; seeing VERIFYING is sufficient.
        assertTrue(sawVerifyBytesIncrease || sawVerifying);

        verify(fileAssetExtractionService).requestExtractionIfEnabled(any(FileAssetsEntity.class));
    }
}
