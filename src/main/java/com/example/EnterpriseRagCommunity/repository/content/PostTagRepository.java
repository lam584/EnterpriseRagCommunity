package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostTagEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostTagSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PostTagRepository extends JpaRepository<PostTagEntity, PostTagEntity.PostTagKey>, JpaSpecificationExecutor<PostTagEntity> {
    Page<PostTagEntity> findByPostId(Long postId, Pageable pageable);
    Page<PostTagEntity> findByTagId(Long tagId, Pageable pageable);
    Page<PostTagEntity> findBySource(PostTagSource source, Pageable pageable);

    boolean existsByTagId(Long tagId);

    interface TagUsageCount {
        Long getTagId();
        Long getUsageCount();
    }

    @Query("select pt.tagId as tagId, count(pt) as usageCount from PostTagEntity pt where pt.tagId in :tagIds group by pt.tagId")
    List<TagUsageCount> countUsageByTagIds(@Param("tagIds") Collection<Long> tagIds);
}

