package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostSuggestionGenConfigRepository extends JpaRepository<PostSuggestionGenConfigEntity, Long> {
    Optional<PostSuggestionGenConfigEntity> findTopByGroupCodeAndKindOrderByUpdatedAtDesc(String groupCode, SuggestionKind kind);
}
