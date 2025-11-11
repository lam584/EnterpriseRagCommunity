package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleLinksRepository extends JpaRepository<UserRoleLinksEntity, UserRoleLinksEntity.UserRoleLinksPk>, JpaSpecificationExecutor<UserRoleLinksEntity> {
    List<UserRoleLinksEntity> findByUserId(Long userId);
    List<UserRoleLinksEntity> findByRoleId(Long roleId);
    void deleteByUserIdAndRoleId(Long userId, Long roleId);
}
