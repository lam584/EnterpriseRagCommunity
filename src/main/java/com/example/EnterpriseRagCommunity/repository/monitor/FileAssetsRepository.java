package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileAssetsRepository extends JpaRepository<FileAssetsEntity, Long>, JpaSpecificationExecutor<FileAssetsEntity> {
    Optional<FileAssetsEntity> findBySha256(String sha256);
}
