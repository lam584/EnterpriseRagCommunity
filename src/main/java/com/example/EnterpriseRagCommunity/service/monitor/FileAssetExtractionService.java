package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FileAssetExtractionService {

    private final FileAssetsRepository fileAssetsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final UploadFormatsConfigService uploadFormatsConfigService;
    private final FileAssetExtractionAsyncService fileAssetExtractionAsyncService;

    @Transactional
    public void requestExtractionIfEnabled(Long fileAssetId) {
        if (fileAssetId == null) return;
        FileAssetsEntity fa = fileAssetsRepository.findById(fileAssetId).orElse(null);
        if (fa == null) return;
        requestExtractionIfEnabled(fa);
    }

    @Transactional
    public void requestExtractionIfEnabled(FileAssetsEntity fa) {
        if (fa == null || fa.getId() == null) return;
        String ext = extLowerOrNull(fa.getOriginalName());
        if (ext == null) ext = extLowerOrNull(fa.getUrl());
        if (ext == null) return;

        var extToRule = uploadFormatsConfigService.enabledExtensionToRule();
        var rule = extToRule.get(ext);
        if (rule == null) return;
        if (!Boolean.TRUE.equals(rule.getParseEnabled())) return;

        FileAssetExtractionsEntity e = fileAssetExtractionsRepository.findById(fa.getId()).orElse(null);
        if (e == null) {
            e = new FileAssetExtractionsEntity();
            e.setFileAssetId(fa.getId());
        }
        e.setExtractStatus(FileAssetExtractionStatus.PENDING);
        e.setErrorMessage(null);
        fileAssetExtractionsRepository.save(e);

        Long id = fa.getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileAssetExtractionAsyncService.extractAsync(id);
                }
            });
        } else {
            fileAssetExtractionAsyncService.extractAsync(id);
        }
    }

    @Transactional(readOnly = true)
    public Optional<FileAssetExtractionsEntity> getExtraction(Long fileAssetId) {
        if (fileAssetId == null) return Optional.empty();
        return fileAssetExtractionsRepository.findById(fileAssetId);
    }

    private static String extLowerOrNull(String fileName) {
        return FileAssetExtractionSupport.extLowerOrNull(fileName);
    }
}
