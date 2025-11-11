package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagsRepository extends JpaRepository<TagsEntity, Long>, JpaSpecificationExecutor<TagsEntity> {
    Optional<TagsEntity> findBySlug(String slug);
    Page<TagsEntity> findByType(TagType type, Pageable pageable);
    Page<TagsEntity> findByIsActiveTrue(Pageable pageable);
    Page<TagsEntity> findByIsSystemFalseAndIsActiveTrue(Pageable pageable);
    Optional<TagsEntity> findByTenantIdAndTypeAndSlug(Long tenantId, TagType type, String slug);
}
