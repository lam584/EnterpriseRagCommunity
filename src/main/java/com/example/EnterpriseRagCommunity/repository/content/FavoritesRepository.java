package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.FavoritesEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface FavoritesRepository extends JpaRepository<FavoritesEntity, Long>, JpaSpecificationExecutor<FavoritesEntity> {
    Page<FavoritesEntity> findByUserId(Long userId, Pageable pageable);
    Page<FavoritesEntity> findByPostId(Long postId, Pageable pageable);
}
