package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<UsersEntity, Long>, JpaSpecificationExecutor<UsersEntity> {
    // Soft-delete aware finders
    Optional<UsersEntity> findByEmailAndIsDeletedFalse(String email);
    Optional<UsersEntity> findByUsernameAndIsDeletedFalse(String username);
    Optional<UsersEntity> findByIdAndIsDeletedFalse(Long id);
    List<UsersEntity> findByIdInAndIsDeletedFalse(Collection<Long> ids);

    List<UsersEntity> findByStatusAndIsDeletedFalse(AccountStatus status);
    List<UsersEntity> findByTenantId_IdAndIsDeletedFalse(Long tenantId); // 使用 tenantId.id 进行查询
    List<UsersEntity> findByLastLoginAtBeforeAndIsDeletedFalse(LocalDateTime before);
    // JSON field sample query using native JSON_EXTRACT with soft-delete guard
    @Query(value = "SELECT * FROM users u WHERE JSON_UNQUOTE(JSON_EXTRACT(u.metadata, ?1)) = ?2 AND u.is_deleted = false", nativeQuery = true)
    List<UsersEntity> findActiveByMetadataPathEquals(String jsonPath, String expectedValue);

    // Add missing method for findByEmail
    Optional<UsersEntity> findByEmail(String email);

    @Query("select u.id as id, u.accessVersion as accessVersion, u.updatedAt as updatedAt, u.sessionInvalidatedAt as sessionInvalidatedAt " +
            "from UsersEntity u where u.email = :email and u.isDeleted = false")
    Optional<UserAccessMetaView> findAccessMetaByEmail(@Param("email") String email);

    interface UserAccessMetaView {
        Long getId();
        Long getAccessVersion();
        LocalDateTime getUpdatedAt();
        LocalDateTime getSessionInvalidatedAt();
    }
}

