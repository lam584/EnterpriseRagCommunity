package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.TenantsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantsRepository extends JpaRepository<TenantsEntity, Long>, JpaSpecificationExecutor<TenantsEntity> {
    Optional<TenantsEntity> findByCode(String code);
    List<TenantsEntity> findByNameContaining(String namePart);

    Optional<TenantsEntity> findFirstByOrderByIdAsc();

    // JSON field sample query using native JSON_EXTRACT
    @Query(value = "SELECT * FROM tenants t WHERE JSON_UNQUOTE(JSON_EXTRACT(t.metadata, ?1)) = ?2", nativeQuery = true)
    List<TenantsEntity> findByMetadataPathEquals(String jsonPath, String expectedValue);
}
