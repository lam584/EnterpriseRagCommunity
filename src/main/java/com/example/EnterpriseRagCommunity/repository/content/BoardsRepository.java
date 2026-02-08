package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BoardsRepository extends JpaRepository<BoardsEntity, Long>, JpaSpecificationExecutor<BoardsEntity> {
    // Basic finders
    List<BoardsEntity> findByParentId(Long parentId);
    List<BoardsEntity> findByTenantId(Long tenantId);
    List<BoardsEntity> findByVisibleTrueOrderBySortOrderAsc();
    Optional<BoardsEntity> findFirstByTenantIdIsNullAndParentIdIsNullAndName(String name);

    // Pageable
    Page<BoardsEntity> findByTenantId(Long tenantId, Pageable pageable);

    // Time range
    Page<BoardsEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
