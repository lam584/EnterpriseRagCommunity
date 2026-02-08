package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostComposeAiSnapshotsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostComposeAiSnapshotsRepository extends JpaRepository<PostComposeAiSnapshotsEntity, Long> {
    Optional<PostComposeAiSnapshotsEntity> findByIdAndUserId(Long id, Long userId);

    Optional<PostComposeAiSnapshotsEntity> findTopByUserIdAndTargetTypeAndDraftIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            PostComposeAiSnapshotTargetType targetType,
            Long draftId,
            PostComposeAiSnapshotStatus status
    );

    Optional<PostComposeAiSnapshotsEntity> findTopByUserIdAndTargetTypeAndPostIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            PostComposeAiSnapshotTargetType targetType,
            Long postId,
            PostComposeAiSnapshotStatus status
    );

    @Modifying
    @Query("""
            update PostComposeAiSnapshotsEntity s
               set s.status = ?3, s.resolvedAt = CURRENT_TIMESTAMP
             where s.userId = ?1
               and s.targetType = com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType.DRAFT
               and s.draftId = ?2
               and s.status = com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus.PENDING
            """)
    int resolvePendingForDraft(Long userId, Long draftId, PostComposeAiSnapshotStatus status);

    @Modifying
    @Query("""
            update PostComposeAiSnapshotsEntity s
               set s.status = ?3, s.resolvedAt = CURRENT_TIMESTAMP
             where s.userId = ?1
               and s.targetType = com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType.POST
               and s.postId = ?2
               and s.status = com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus.PENDING
            """)
    int resolvePendingForPost(Long userId, Long postId, PostComposeAiSnapshotStatus status);
}
