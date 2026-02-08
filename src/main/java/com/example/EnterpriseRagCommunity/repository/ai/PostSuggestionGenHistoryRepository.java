package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostSuggestionGenHistoryRepository extends JpaRepository<PostSuggestionGenHistoryEntity, Long> {
    Page<PostSuggestionGenHistoryEntity> findByKindOrderByCreatedAtDesc(SuggestionKind kind, Pageable pageable);

    Page<PostSuggestionGenHistoryEntity> findByKindAndUserIdOrderByCreatedAtDesc(SuggestionKind kind, Long userId, Pageable pageable);
}
