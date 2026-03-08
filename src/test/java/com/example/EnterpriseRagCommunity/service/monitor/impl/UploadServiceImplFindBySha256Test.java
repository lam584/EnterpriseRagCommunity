package com.example.EnterpriseRagCommunity.service.monitor.impl;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UploadServiceImplFindBySha256Test {
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findBySha256_whenHit_shouldReturnExisting() {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        FileAssetExtractionService fileAssetExtractionService = mock(FileAssetExtractionService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", java.util.List.of()));

        String sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
        FileAssetsEntity existing = new FileAssetsEntity();
        existing.setId(9L);
        existing.setUrl("/uploads/x");
        existing.setOriginalName("x.txt");
        existing.setSizeBytes(5L);
        existing.setMimeType("text/plain");
        existing.setSha256(sha256);
        when(fileAssetsRepository.findBySha256(eq(sha256))).thenReturn(Optional.of(existing));

        UploadServiceImpl svc = new UploadServiceImpl();
        ReflectionTestUtils.setField(svc, "fileAssetsRepository", fileAssetsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "fileAssetExtractionService", fileAssetExtractionService);
        ReflectionTestUtils.setField(svc, "uploadRoot", "uploads");
        ReflectionTestUtils.setField(svc, "urlPrefix", "/uploads");

        UploadResultDTO out = svc.findBySha256(sha256, "a.txt");
        assertEquals(9L, out.getId());

        verify(fileAssetExtractionService).requestExtractionIfEnabled(existing);
        verify(fileAssetsRepository, never()).save(any());
    }

    @Test
    void findBySha256_whenMiss_shouldThrowNotFound() {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        FileAssetExtractionService fileAssetExtractionService = mock(FileAssetExtractionService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", java.util.List.of()));

        String sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
        when(fileAssetsRepository.findBySha256(eq(sha256))).thenReturn(Optional.empty());

        UploadServiceImpl svc = new UploadServiceImpl();
        ReflectionTestUtils.setField(svc, "fileAssetsRepository", fileAssetsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "fileAssetExtractionService", fileAssetExtractionService);
        ReflectionTestUtils.setField(svc, "uploadRoot", "uploads");
        ReflectionTestUtils.setField(svc, "urlPrefix", "/uploads");

        assertThrows(ResourceNotFoundException.class, () -> svc.findBySha256(sha256, "a.txt"));
    }

    @Test
    void findBySha256_whenInvalid_shouldThrowBadRequest() {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        FileAssetExtractionService fileAssetExtractionService = mock(FileAssetExtractionService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", java.util.List.of()));

        UploadServiceImpl svc = new UploadServiceImpl();
        ReflectionTestUtils.setField(svc, "fileAssetsRepository", fileAssetsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "fileAssetExtractionService", fileAssetExtractionService);
        ReflectionTestUtils.setField(svc, "uploadRoot", "uploads");
        ReflectionTestUtils.setField(svc, "urlPrefix", "/uploads");

        assertThrows(IllegalArgumentException.class, () -> svc.findBySha256("nope", "a.txt"));
    }
}

