package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<UsersEntity, Long>, JpaSpecificationExecutor<UsersEntity> {
    // Soft-delete aware finders
    Optional<UsersEntity> findByEmailAndIsDeletedFalse(String email);
    Optional<UsersEntity> findByUsernameAndIsDeletedFalse(String username);
    Optional<UsersEntity> findByIdAndIsDeletedFalse(Long id);

    List<UsersEntity> findByStatusAndIsDeletedFalse(AccountStatus status);
    List<UsersEntity> findByTenantId_IdAndIsDeletedFalse(Long tenantId); // 使用 tenantId.id 进行查询
    List<UsersEntity> findByLastLoginAtBeforeAndIsDeletedFalse(LocalDateTime before);
    // JSON field sample query using native JSON_EXTRACT with soft-delete guard
    @Query(value = "SELECT * FROM users u WHERE JSON_UNQUOTE(JSON_EXTRACT(u.metadata, ?1)) = ?2 AND u.is_deleted = false", nativeQuery = true)
    List<UsersEntity> findActiveByMetadataPathEquals(String jsonPath, String expectedValue);

    // Add missing method for findByEmail
    Optional<UsersEntity> findByEmail(String email);
}

