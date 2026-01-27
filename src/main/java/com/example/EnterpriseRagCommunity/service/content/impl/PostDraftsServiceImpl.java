package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostDraftsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostDraftsEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostDraftsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PostDraftsService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class PostDraftsServiceImpl implements PostDraftsService {

    @Autowired
    private PostDraftsRepository postDraftsRepository;

    @Autowired
    private AdministratorService administratorService;

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
        dto.setId(e.getId());
        dto.setTenantId(e.getTenantId());
        dto.setBoardId(e.getBoardId());
        dto.setAuthorId(e.getAuthorId());
        dto.setTitle(e.getTitle());
        dto.setContent(e.getContent());
        dto.setContentFormat(e.getContentFormat());
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
        e.setTitle(dto.getTitle() == null ? "" : dto.getTitle().trim());
        e.setContent(dto.getContent() == null ? "" : dto.getContent());
        e.setContentFormat(dto.getContentFormat());
        e.setMetadata(dto.getMetadata());
        e = postDraftsRepository.save(e);
        return toDTO(e);
    }

    @Override
    @Transactional
    public PostDraftsDTO updateMine(Long id, PostDraftsUpdateDTO dto) {
        Long me = currentUserIdOrThrow();
        PostDraftsEntity e = postDraftsRepository.findByIdAndAuthorId(id, me)
                .orElseThrow(() -> new IllegalArgumentException("草稿不存在或无权访问"));
        e.setBoardId(dto.getBoardId());
        e.setTitle(dto.getTitle() == null ? "" : dto.getTitle().trim());
        e.setContent(dto.getContent() == null ? "" : dto.getContent());
        e.setContentFormat(dto.getContentFormat());
        e.setMetadata(dto.getMetadata());
        e = postDraftsRepository.save(e);
        return toDTO(e);
    }

    @Override
    @Transactional
    public void deleteMine(Long id) {
        Long me = currentUserIdOrThrow();
        PostDraftsEntity e = postDraftsRepository.findByIdAndAuthorId(id, me)
                .orElseThrow(() -> new IllegalArgumentException("草稿不存在或无权访问"));
        postDraftsRepository.delete(e);
    }
}

