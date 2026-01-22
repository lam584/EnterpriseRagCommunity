package com.example.EnterpriseRagCommunity.repository.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VectorIndicesRepository extends JpaRepository<VectorIndicesEntity, Long>, JpaSpecificationExecutor<VectorIndicesEntity> {
    List<VectorIndicesEntity> findByProvider(VectorIndexProvider provider);
    List<VectorIndicesEntity> findByStatus(VectorIndexStatus status);
    List<VectorIndicesEntity> findByCollectionName(String collectionName);
    boolean existsByProviderAndCollectionName(VectorIndexProvider provider, String collectionName);
}

