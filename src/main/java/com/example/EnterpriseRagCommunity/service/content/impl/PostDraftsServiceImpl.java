package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostDraftsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.PostDraftsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.repository.content.PostDraftsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.CurrentUsernameResolver;
import com.example.EnterpriseRagCommunity.service.content.PostDraftsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostDraftsServiceImpl implements PostDraftsService {
    private final PostDraftsRepository postDraftsRepository;
    private final AdministratorService administratorService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    private static PostDraftsDTO toDTO(PostDraftsEntity e) {
        PostDraftsDTO dto = new PostDraftsDTO();
        PostContentFieldSupport.applyCommonFields(
                e.getId(),
                e.getTenantId(),
                e.getBoardId(),
                e.getAuthorId(),
                e.getTitle(),
                e.getContent(),
                e.getContentFormat(),
                dto::setId,
                dto::setTenantId,
                dto::setBoardId,
                dto::setAuthorId,
                dto::setTitle,
                dto::setContent,
                dto::setContentFormat
        );
        dto.setMetadata(e.getMetadata());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    @Override
    public Page<PostDraftsDTO> listMine(Pageable pageable) {
        Long me = currentUserIdOrThrow();
        return postDraftsRepository.findByAuthorIdOrderByUpdatedAtDesc(me, pageable).map(PostDraftsServiceImpl::toDTO);
    }

    @Override
    public PostDraftsDTO getMine(Long id) {
        Long me = currentUserIdOrThrow();
        PostDraftsEntity e = postDraftsRepository.findByIdAndAuthorId(id, me)
                .orElseThrow(() -> new IllegalArgumentException("草稿不存在或无权访问"));
        return toDTO(e);
    }

    @Override
    @Transactional
    public PostDraftsDTO create(PostDraftsCreateDTO dto) {
        Long me = currentUserIdOrThrow();
        PostDraftsEntity e = new PostDraftsEntity();
        e.setTenantId(dto.getTenantId());
        e.setBoardId(dto.getBoardId());
        e.setAuthorId(me);
        applyDraftFields(e, dto.getTitle(), dto.getContent(), dto.getContentFormat(), dto.getMetadata());
        e = postDraftsRepository.save(e);
        auditLogWriter.write(
                me,
                currentUsernameOrNull(),
                "POST_DRAFT_CREATE",
                "POST_DRAFT",
                e.getId(),
                AuditResult.SUCCESS,
                "创建草稿",
                null,
                auditDiffBuilder.build(Map.of(), summarizeForAudit(e))
        );
        return toDTO(e);
    }

    @Override
    @Transactional
    public PostDraftsDTO updateMine(Long id, PostDraftsUpdateDTO dto) {
        Long me = currentUserIdOrThrow();
        PostDraftsEntity e = postDraftsRepository.findByIdAndAuthorId(id, me)
                .orElseThrow(() -> new IllegalArgumentException("草稿不存在或无权访问"));
        Map<String, Object> before = summarizeForAudit(e);
        e.setBoardId(dto.getBoardId());
        applyDraftFields(e, dto.getTitle(), dto.getContent(), dto.getContentFormat(), dto.getMetadata());
        e = postDraftsRepository.save(e);
        auditLogWriter.write(
                me,
                currentUsernameOrNull(),
                "POST_DRAFT_UPDATE",
                "POST_DRAFT",
                e.getId(),
                AuditResult.SUCCESS,
                "更新草稿",
                null,
                auditDiffBuilder.build(before, summarizeForAudit(e))
        );
        return toDTO(e);
    }

    @Override
    @Transactional
    public void deleteMine(Long id) {
        Long me = currentUserIdOrThrow();
        PostDraftsEntity e = postDraftsRepository.findByIdAndAuthorId(id, me)
                .orElseThrow(() -> new IllegalArgumentException("草稿不存在或无权访问"));
        Map<String, Object> before = summarizeForAudit(e);
        postDraftsRepository.delete(e);
        auditLogWriter.write(
                me,
                currentUsernameOrNull(),
                "POST_DRAFT_DELETE",
                "POST_DRAFT",
                id,
                AuditResult.SUCCESS,
                "删除草稿",
                null,
                auditDiffBuilder.build(before, Map.of())
        );
    }

    private static Map<String, Object> summarizeForAudit(PostDraftsEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (e == null) return m;
        m.put("id", e.getId());
        m.put("tenantId", e.getTenantId());
        m.put("boardId", e.getBoardId());
        m.put("authorId", e.getAuthorId());
        m.put("titleLen", e.getTitle() == null ? 0 : e.getTitle().length());
        m.put("contentLen", e.getContent() == null ? 0 : e.getContent().length());
        m.put("contentFormat", e.getContentFormat());
        Object meta = e.getMetadata();
        if (meta instanceof Map<?, ?> mm) {
            m.put("metadataKeys", mm.keySet().stream().filter(k -> k != null).map(String::valueOf).limit(20).toList());
            m.put("metadataKeyCount", mm.size());
        } else {
            m.put("metadataKeys", null);
            m.put("metadataKeyCount", 0);
        }
        return m;
    }

    private static void applyDraftFields(
            PostDraftsEntity entity,
            String title,
            String content,
            ContentFormat contentFormat,
            Map<String, Object> metadata
    ) {
        entity.setTitle(title == null ? "" : title.trim());
        entity.setContent(content == null ? "" : content);
        entity.setContentFormat(contentFormat);
        entity.setMetadata(metadata);
    }

    private static String currentUsernameOrNull() {
        return CurrentUsernameResolver.currentUsernameOrNull();
    }
}
