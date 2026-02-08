package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotApplyRequest;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotDTO;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;

public interface PostComposeAiSnapshotsService {
    PostComposeAiSnapshotDTO create(PostComposeAiSnapshotCreateRequest req);

    PostComposeAiSnapshotDTO getPending(PostComposeAiSnapshotTargetType targetType, Long draftId, Long postId);

    PostComposeAiSnapshotDTO apply(Long snapshotId, PostComposeAiSnapshotApplyRequest req);

    PostComposeAiSnapshotDTO revert(Long snapshotId);
}

