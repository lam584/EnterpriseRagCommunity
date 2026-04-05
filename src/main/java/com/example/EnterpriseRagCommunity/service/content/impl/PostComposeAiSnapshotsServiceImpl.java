package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotApplyRequest;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostComposeAiSnapshotsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostDraftsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import com.example.EnterpriseRagCommunity.repository.content.PostComposeAiSnapshotsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostDraftsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.CurrentUserIdResolver;
import com.example.EnterpriseRagCommunity.service.content.PostComposeAiSnapshotsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostComposeAiSnapshotsServiceImpl implements PostComposeAiSnapshotsService {

    private static final int DEFAULT_EXPIRES_SECONDS = 120;
    private final PostComposeAiSnapshotsRepository snapshotsRepository;
    private final PostDraftsRepository postDraftsRepository;
    private final PostsRepository postsRepository;
    private final AdministratorService administratorService;

    private Long currentUserIdOrThrow() {
        return CurrentUserIdResolver.currentUserIdOrThrow(
                administratorService,
                () -> new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {},
                () -> new IllegalArgumentException("当前用户不存在")
        );
    }

    private static PostComposeAiSnapshotDTO toDTO(PostComposeAiSnapshotsEntity e) {
        PostComposeAiSnapshotDTO dto = new PostComposeAiSnapshotDTO();
        dto.setId(e.getId());
        dto.setTenantId(e.getTenantId());
        dto.setUserId(e.getUserId());
        dto.setTargetType(e.getTargetType());
        dto.setDraftId(e.getDraftId());
        dto.setPostId(e.getPostId());
        dto.setBeforeTitle(e.getBeforeTitle());
        dto.setBeforeContent(e.getBeforeContent());
        dto.setBeforeBoardId(e.getBeforeBoardId());
        dto.setBeforeMetadata(e.getBeforeMetadata());
        dto.setAfterContent(e.getAfterContent());
        dto.setInstruction(e.getInstruction());
        dto.setProviderId(e.getProviderId());
        dto.setModel(e.getModel());
        dto.setTemperature(e.getTemperature());
        dto.setTopP(e.getTopP());
        dto.setStatus(e.getStatus());
        dto.setExpiresAt(e.getExpiresAt());
        dto.setResolvedAt(e.getResolvedAt());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    @Override
    @Transactional
    public PostComposeAiSnapshotDTO create(PostComposeAiSnapshotCreateRequest req) {
        Long me = currentUserIdOrThrow();

        if (req.getTargetType() == PostComposeAiSnapshotTargetType.DRAFT) {
            Long draftId = req.getDraftId();
            if (draftId == null || draftId <= 0) throw new IllegalArgumentException("draftId不能为空");
            PostDraftsEntity d = postDraftsRepository.findByIdAndAuthorId(draftId, me)
                    .orElseThrow(() -> new IllegalArgumentException("草稿不存在或无权访问"));
            snapshotsRepository.resolvePendingForDraft(me, draftId, PostComposeAiSnapshotStatus.EXPIRED);

            PostComposeAiSnapshotsEntity e = new PostComposeAiSnapshotsEntity();
            e.setTenantId(d.getTenantId());
            e.setUserId(me);
            e.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
            e.setDraftId(draftId);
            e.setPostId(null);
            return savePendingSnapshot(e, req);
        }

        if (req.getTargetType() == PostComposeAiSnapshotTargetType.POST) {
            Long postId = req.getPostId();
            if (postId == null || postId <= 0) throw new IllegalArgumentException("postId不能为空");
            PostsEntity p = postsRepository.findByIdAndIsDeletedFalse(postId)
                    .orElseThrow(() -> new IllegalArgumentException("帖子不存在或无权访问"));
            if (!me.equals(p.getAuthorId())) throw new IllegalArgumentException("帖子不存在或无权访问");
            snapshotsRepository.resolvePendingForPost(me, postId, PostComposeAiSnapshotStatus.EXPIRED);

            PostComposeAiSnapshotsEntity e = new PostComposeAiSnapshotsEntity();
            e.setTenantId(p.getTenantId());
            e.setUserId(me);
            e.setTargetType(PostComposeAiSnapshotTargetType.POST);
            e.setDraftId(null);
            e.setPostId(postId);
            return savePendingSnapshot(e, req);
        }

        throw new IllegalArgumentException("不支持的targetType");
    }

    @Override
    public PostComposeAiSnapshotDTO getPending(PostComposeAiSnapshotTargetType targetType, Long draftId, Long postId) {
        Long me = currentUserIdOrThrow();
        Optional<PostComposeAiSnapshotsEntity> found;
        if (targetType == PostComposeAiSnapshotTargetType.DRAFT) {
            if (draftId == null || draftId <= 0) throw new IllegalArgumentException("draftId不能为空");
            found = snapshotsRepository.findTopByUserIdAndTargetTypeAndDraftIdAndStatusOrderByCreatedAtDesc(
                    me,
                    PostComposeAiSnapshotTargetType.DRAFT,
                    draftId,
                    PostComposeAiSnapshotStatus.PENDING
            );
        } else if (targetType == PostComposeAiSnapshotTargetType.POST) {
            if (postId == null || postId <= 0) throw new IllegalArgumentException("postId不能为空");
            found = snapshotsRepository.findTopByUserIdAndTargetTypeAndPostIdAndStatusOrderByCreatedAtDesc(
                    me,
                    PostComposeAiSnapshotTargetType.POST,
                    postId,
                    PostComposeAiSnapshotStatus.PENDING
            );
        } else {
            throw new IllegalArgumentException("不支持的targetType");
        }
        return found.map(PostComposeAiSnapshotsServiceImpl::toDTO).orElse(null);
    }

    @Override
    @Transactional
    public PostComposeAiSnapshotDTO apply(Long snapshotId, PostComposeAiSnapshotApplyRequest req) {
        Long me = currentUserIdOrThrow();
        PostComposeAiSnapshotsEntity e = snapshotsRepository.findByIdAndUserId(snapshotId, me)
                .orElseThrow(() -> new IllegalArgumentException("快照不存在或无权访问"));
        if (e.getStatus() != PostComposeAiSnapshotStatus.PENDING) {
            return toDTO(e);
        }
        e.setAfterContent(req.getAfterContent());
        e.setStatus(PostComposeAiSnapshotStatus.APPLIED);
        e.setResolvedAt(LocalDateTime.now());
        e = snapshotsRepository.save(e);
        return toDTO(e);
    }

    @Override
    @Transactional
    public PostComposeAiSnapshotDTO revert(Long snapshotId) {
        Long me = currentUserIdOrThrow();
        PostComposeAiSnapshotsEntity e = snapshotsRepository.findByIdAndUserId(snapshotId, me)
                .orElseThrow(() -> new IllegalArgumentException("快照不存在或无权访问"));
        if (e.getStatus() != PostComposeAiSnapshotStatus.PENDING) {
            return toDTO(e);
        }
        e.setStatus(PostComposeAiSnapshotStatus.REVERTED);
        e.setResolvedAt(LocalDateTime.now());
        e = snapshotsRepository.save(e);
        return toDTO(e);
    }

    private PostComposeAiSnapshotDTO savePendingSnapshot(
            PostComposeAiSnapshotsEntity entity,
            PostComposeAiSnapshotCreateRequest req
    ) {
        entity.setBeforeTitle(normTitle(req.getBeforeTitle()));
        entity.setBeforeContent(normContent(req.getBeforeContent()));
        entity.setBeforeBoardId(req.getBeforeBoardId());
        entity.setBeforeMetadata(req.getBeforeMetadata());
        entity.setAfterContent(null);
        entity.setInstruction(blankToNull(req.getInstruction()));
        entity.setProviderId(blankToNull(req.getProviderId()));
        entity.setModel(blankToNull(req.getModel()));
        entity.setTemperature(req.getTemperature());
        entity.setTopP(req.getTopP());
        entity.setStatus(PostComposeAiSnapshotStatus.PENDING);
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(DEFAULT_EXPIRES_SECONDS));
        entity.setResolvedAt(null);
        return toDTO(snapshotsRepository.save(entity));
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normTitle(String s) {
        String t = s == null ? "" : s.trim();
        if (t.length() > 191) t = t.substring(0, 191);
        return t;
    }

    private static String normContent(String s) {
        return s == null ? "" : s;
    }
}
