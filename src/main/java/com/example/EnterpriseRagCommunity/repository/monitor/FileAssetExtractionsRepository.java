package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileAssetExtractionsRepository extends JpaRepository<FileAssetExtractionsEntity, Long> {
    Page<FileAssetExtractionsEntity> findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(FileAssetExtractionStatus extractStatus, Long fileAssetId, Pageable pageable);
}
