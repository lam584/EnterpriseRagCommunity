package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class FileAssetExtractionAfterCommitTest {

    @Autowired
    FileAssetsRepository fileAssetsRepository;

    @Autowired
    FileAssetExtractionsRepository fileAssetExtractionsRepository;

    @Autowired
    FileAssetExtractionService fileAssetExtractionService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    FileAssetExtractionAsyncService fileAssetExtractionAsyncService;

    @Test
    void requestExtraction_shouldTriggerAsyncAfterCommit() {
        String randomSha = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath("C:/tmp/placeholder.txt");
        fa.setUrl("/uploads/placeholder.txt");
        fa.setOriginalName("placeholder.txt");
        fa.setSizeBytes(1L);
        fa.setMimeType("text/plain");
        fa.setSha256(randomSha.substring(0, 64));
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);
        Long fileAssetId = fa.getId();

        AtomicBoolean called = new AtomicBoolean(false);
        doAnswer(inv -> {
            called.set(true);
            return null;
        }).when(fileAssetExtractionAsyncService).extractAsync(eq(fileAssetId));

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.executeWithoutResult(st -> {
            fileAssetExtractionService.requestExtractionIfEnabled(fileAssetId);
            assertFalse(called.get());
        });

        assertTrue(called.get());
        assertTrue(fileAssetExtractionsRepository.findById(fileAssetId).isPresent());
    }
}
