package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotApplyRequest;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostComposeAiSnapshotsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostDraftsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import com.example.EnterpriseRagCommunity.repository.content.PostComposeAiSnapshotsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostDraftsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostComposeAiSnapshotsServiceImplBranchUnitTest {

    @AfterEach
    void cleanup() {
        SecurityContextTestSupport.clear();
    }

    private static PostComposeAiSnapshotsServiceImpl newService(
            PostComposeAiSnapshotsRepository snapshotsRepository,
            PostDraftsRepository postDraftsRepository,
            PostsRepository postsRepository,
            AdministratorService administratorService
    ) {
        PostComposeAiSnapshotsServiceImpl svc = new PostComposeAiSnapshotsServiceImpl();
        ReflectionTestUtils.setField(svc, "snapshotsRepository", snapshotsRepository);
        ReflectionTestUtils.setField(svc, "postDraftsRepository", postDraftsRepository);
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        return svc;
    }

    private static void mockAuth(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static Authentication mockAuthenticatedEmail(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(email);
        when(auth.getName()).thenReturn(email);
        return auth;
    }

    private static UsersEntity userWithId(long id) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        return u;
    }

    private static PostComposeAiSnapshotCreateRequest draftReq(Long draftId, String beforeTitle, String beforeContent, String instruction, String providerId, String model) {
        PostComposeAiSnapshotCreateRequest req = new PostComposeAiSnapshotCreateRequest();
        req.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
        req.setDraftId(draftId);
        req.setBeforeTitle(beforeTitle);
        req.setBeforeContent(beforeContent);
        req.setBeforeBoardId(10L);
        req.setBeforeMetadata(Map.of("k", "v"));
        req.setInstruction(instruction);
        req.setProviderId(providerId);
        req.setModel(model);
        req.setTemperature(0.7);
        req.setTopP(0.9);
        return req;
    }

    private static PostComposeAiSnapshotCreateRequest postReq(Long postId, String beforeTitle, String beforeContent, String providerId, String model) {
        PostComposeAiSnapshotCreateRequest req = new PostComposeAiSnapshotCreateRequest();
        req.setTargetType(PostComposeAiSnapshotTargetType.POST);
        req.setPostId(postId);
        req.setBeforeTitle(beforeTitle);
        req.setBeforeContent(beforeContent);
        req.setBeforeBoardId(11L);
        req.setProviderId(providerId);
        req.setModel(model);
        return req;
    }

    @Test
    void create_authNull_throwsAuthenticationException() {
        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> svc.create(draftReq(1L, "t", "c", null, null, null)));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);
    }

    @Test
    void getPending_notAuthenticated_throwsAuthenticationException() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(auth.getPrincipal()).thenReturn("u@example.com");
        when(auth.getName()).thenReturn("u@example.com");
        mockAuth(auth);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> svc.getPending(PostComposeAiSnapshotTargetType.DRAFT, 1L, null));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);
    }

    @Test
    void revert_anonymousUser_throwsAuthenticationException() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        when(auth.getName()).thenReturn("anonymousUser");
        mockAuth(auth);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> svc.revert(1L));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);
    }

    @Test
    void getPending_userMissing_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.getPending(PostComposeAiSnapshotTargetType.DRAFT, 1L, null));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository);
    }

    @Test
    void create_draft_draftIdNull_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.create(draftReq(null, "t", "c", null, null, null)));
        verifyNoInteractions(postDraftsRepository, snapshotsRepository, postsRepository);
    }

    @Test
    void create_draft_draftIdZero_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.create(draftReq(0L, "t", "c", null, null, null)));
        verifyNoInteractions(postDraftsRepository, snapshotsRepository, postsRepository);
    }

    @Test
    void create_draft_draftMissing_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        when(postDraftsRepository.findByIdAndAuthorId(eq(5L), eq(10L))).thenReturn(Optional.empty());

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.create(draftReq(5L, "t", "c", null, null, null)));
        verify(snapshotsRepository, never()).resolvePendingForDraft(any(), any(), any());
        verify(snapshotsRepository, never()).save(any());
    }

    @Test
    void create_draft_success_normalizes_fields_and_expiresPending() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostDraftsEntity d = new PostDraftsEntity();
        d.setId(7L);
        d.setTenantId(99L);
        d.setAuthorId(10L);
        when(postDraftsRepository.findByIdAndAuthorId(eq(7L), eq(10L))).thenReturn(Optional.of(d));

        when(snapshotsRepository.save(any(PostComposeAiSnapshotsEntity.class))).thenAnswer(inv -> {
            PostComposeAiSnapshotsEntity e = inv.getArgument(0);
            e.setId(123L);
            return e;
        });

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        String longTitle = "  " + "x".repeat(400) + "  ";
        LocalDateTime t0 = LocalDateTime.now();
        PostComposeAiSnapshotDTO dto = svc.create(draftReq(7L, longTitle, null, "  inst  ", "   ", null));

        ArgumentCaptor<PostComposeAiSnapshotsEntity> captor = ArgumentCaptor.forClass(PostComposeAiSnapshotsEntity.class);
        verify(snapshotsRepository).resolvePendingForDraft(eq(10L), eq(7L), eq(PostComposeAiSnapshotStatus.EXPIRED));
        verify(snapshotsRepository).save(captor.capture());

        PostComposeAiSnapshotsEntity saved = captor.getValue();
        assertEquals(99L, saved.getTenantId());
        assertEquals(10L, saved.getUserId());
        assertEquals(PostComposeAiSnapshotTargetType.DRAFT, saved.getTargetType());
        assertEquals(7L, saved.getDraftId());
        assertNull(saved.getPostId());
        assertEquals(191, saved.getBeforeTitle().length());
        assertEquals("", saved.getBeforeContent());
        assertEquals(10L, saved.getBeforeBoardId());
        assertEquals(Map.of("k", "v"), saved.getBeforeMetadata());
        assertEquals("inst", saved.getInstruction());
        assertNull(saved.getProviderId());
        assertNull(saved.getModel());
        assertEquals(PostComposeAiSnapshotStatus.PENDING, saved.getStatus());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(t0.minusSeconds(1)));
        assertTrue(saved.getExpiresAt().isBefore(t0.plusSeconds(121)));
        assertNull(saved.getResolvedAt());

        assertNotNull(dto);
        assertEquals(123L, dto.getId());
        assertEquals(PostComposeAiSnapshotStatus.PENDING, dto.getStatus());
        assertEquals(saved.getBeforeTitle(), dto.getBeforeTitle());
        assertEquals(saved.getBeforeContent(), dto.getBeforeContent());
    }

    @Test
    void create_post_postIdNull_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.create(postReq(null, "t", "c", null, null)));
        verifyNoInteractions(postsRepository, snapshotsRepository, postDraftsRepository);
    }

    @Test
    void create_post_postIdZero_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.create(postReq(0L, "t", "c", null, null)));
        verifyNoInteractions(postsRepository, snapshotsRepository, postDraftsRepository);
    }

    @Test
    void create_post_postMissing_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        when(postsRepository.findByIdAndIsDeletedFalse(eq(5L))).thenReturn(Optional.empty());

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.create(postReq(5L, "t", "c", null, null)));
        verify(snapshotsRepository, never()).resolvePendingForPost(any(), any(), any());
        verify(snapshotsRepository, never()).save(any());
    }

    @Test
    void create_post_notAuthor_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostsEntity p = new PostsEntity();
        p.setId(6L);
        p.setTenantId(1L);
        p.setAuthorId(999L);
        when(postsRepository.findByIdAndIsDeletedFalse(eq(6L))).thenReturn(Optional.of(p));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.create(postReq(6L, "t", "c", null, null)));
        verify(snapshotsRepository, never()).resolvePendingForPost(any(), any(), any());
        verify(snapshotsRepository, never()).save(any());
    }

    @Test
    void create_post_success_normalizes_fields_and_expiresPending() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostsEntity p = new PostsEntity();
        p.setId(6L);
        p.setTenantId(2L);
        p.setAuthorId(10L);
        when(postsRepository.findByIdAndIsDeletedFalse(eq(6L))).thenReturn(Optional.of(p));

        when(snapshotsRepository.save(any(PostComposeAiSnapshotsEntity.class))).thenAnswer(inv -> {
            PostComposeAiSnapshotsEntity e = inv.getArgument(0);
            e.setId(456L);
            return e;
        });

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotDTO dto = svc.create(postReq(6L, null, "content", "  pid  ", "   "));

        ArgumentCaptor<PostComposeAiSnapshotsEntity> captor = ArgumentCaptor.forClass(PostComposeAiSnapshotsEntity.class);
        verify(snapshotsRepository).resolvePendingForPost(eq(10L), eq(6L), eq(PostComposeAiSnapshotStatus.EXPIRED));
        verify(snapshotsRepository).save(captor.capture());

        PostComposeAiSnapshotsEntity saved = captor.getValue();
        assertEquals(2L, saved.getTenantId());
        assertEquals(10L, saved.getUserId());
        assertEquals(PostComposeAiSnapshotTargetType.POST, saved.getTargetType());
        assertNull(saved.getDraftId());
        assertEquals(6L, saved.getPostId());
        assertEquals("", saved.getBeforeTitle());
        assertEquals("content", saved.getBeforeContent());
        assertEquals("pid", saved.getProviderId());
        assertNull(saved.getModel());
        assertEquals(PostComposeAiSnapshotStatus.PENDING, saved.getStatus());
        assertNotNull(saved.getExpiresAt());

        assertEquals(456L, dto.getId());
        assertEquals(PostComposeAiSnapshotTargetType.POST, dto.getTargetType());
    }

    @Test
    void create_unsupportedTargetType_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotCreateRequest req = new PostComposeAiSnapshotCreateRequest();
        req.setTargetType(null);

        assertThrows(IllegalArgumentException.class, () -> svc.create(req));
        verifyNoInteractions(postsRepository, postDraftsRepository, snapshotsRepository);
    }

    @Test
    void getPending_draft_draftIdNull_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.getPending(PostComposeAiSnapshotTargetType.DRAFT, null, null));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository);
    }

    @Test
    void getPending_draft_draftIdZero_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.getPending(PostComposeAiSnapshotTargetType.DRAFT, 0L, null));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository);
    }

    @Test
    void getPending_post_postIdNull_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.getPending(PostComposeAiSnapshotTargetType.POST, null, null));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository);
    }

    @Test
    void getPending_post_postIdZero_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.getPending(PostComposeAiSnapshotTargetType.POST, null, 0L));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository);
    }

    @Test
    void getPending_unsupportedTargetType_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.getPending(null, 1L, 1L));
        verifyNoInteractions(snapshotsRepository, postDraftsRepository, postsRepository);
    }

    @Test
    void getPending_draft_found_returnsDto() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsEntity e = new PostComposeAiSnapshotsEntity();
        e.setId(1L);
        e.setTenantId(2L);
        e.setUserId(10L);
        e.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
        e.setDraftId(7L);
        e.setBeforeTitle("t");
        e.setBeforeContent("c");
        e.setBeforeBoardId(10L);
        e.setBeforeMetadata(Map.of("k", "v"));
        e.setStatus(PostComposeAiSnapshotStatus.PENDING);

        when(snapshotsRepository.findTopByUserIdAndTargetTypeAndDraftIdAndStatusOrderByCreatedAtDesc(
                eq(10L),
                eq(PostComposeAiSnapshotTargetType.DRAFT),
                eq(7L),
                eq(PostComposeAiSnapshotStatus.PENDING)
        )).thenReturn(Optional.of(e));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotDTO dto = svc.getPending(PostComposeAiSnapshotTargetType.DRAFT, 7L, null);
        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals(PostComposeAiSnapshotTargetType.DRAFT, dto.getTargetType());
        assertEquals("t", dto.getBeforeTitle());
        assertEquals("c", dto.getBeforeContent());
    }

    @Test
    void getPending_post_notFound_returnsNull() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        when(snapshotsRepository.findTopByUserIdAndTargetTypeAndPostIdAndStatusOrderByCreatedAtDesc(
                eq(10L),
                eq(PostComposeAiSnapshotTargetType.POST),
                eq(8L),
                eq(PostComposeAiSnapshotStatus.PENDING)
        )).thenReturn(Optional.empty());

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotDTO dto = svc.getPending(PostComposeAiSnapshotTargetType.POST, null, 8L);
        assertNull(dto);
    }

    @Test
    void apply_snapshotMissing_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(10L))).thenReturn(Optional.empty());

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotApplyRequest req = new PostComposeAiSnapshotApplyRequest();
        req.setAfterContent("after");

        assertThrows(IllegalArgumentException.class, () -> svc.apply(1L, req));
        verify(snapshotsRepository, never()).save(any());
    }

    @Test
    void apply_notPending_returnsDto_andDoesNotSave() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsEntity e = new PostComposeAiSnapshotsEntity();
        e.setId(1L);
        e.setUserId(10L);
        e.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
        e.setBeforeTitle("t");
        e.setBeforeContent("c");
        e.setBeforeBoardId(10L);
        e.setStatus(PostComposeAiSnapshotStatus.APPLIED);

        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(10L))).thenReturn(Optional.of(e));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotApplyRequest req = new PostComposeAiSnapshotApplyRequest();
        req.setAfterContent("after");

        PostComposeAiSnapshotDTO dto = svc.apply(1L, req);
        assertEquals(PostComposeAiSnapshotStatus.APPLIED, dto.getStatus());
        verify(snapshotsRepository, never()).save(any());
    }

    @Test
    void apply_pending_updatesAndSaves() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsEntity e = new PostComposeAiSnapshotsEntity();
        e.setId(1L);
        e.setUserId(10L);
        e.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
        e.setBeforeTitle("t");
        e.setBeforeContent("c");
        e.setBeforeBoardId(10L);
        e.setStatus(PostComposeAiSnapshotStatus.PENDING);

        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(10L))).thenReturn(Optional.of(e));
        when(snapshotsRepository.save(any(PostComposeAiSnapshotsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotApplyRequest req = new PostComposeAiSnapshotApplyRequest();
        req.setAfterContent("after");

        PostComposeAiSnapshotDTO dto = svc.apply(1L, req);
        assertEquals(PostComposeAiSnapshotStatus.APPLIED, dto.getStatus());
        assertEquals("after", dto.getAfterContent());
        assertNotNull(dto.getResolvedAt());
        verify(snapshotsRepository).save(eq(e));
    }

    @Test
    void revert_snapshotMissing_throwsIllegalArgumentException() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(10L))).thenReturn(Optional.empty());

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        assertThrows(IllegalArgumentException.class, () -> svc.revert(1L));
        verify(snapshotsRepository, never()).save(any());
    }

    @Test
    void revert_notPending_returnsDto_andDoesNotSave() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsEntity e = new PostComposeAiSnapshotsEntity();
        e.setId(2L);
        e.setUserId(10L);
        e.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
        e.setBeforeTitle("t");
        e.setBeforeContent("c");
        e.setBeforeBoardId(10L);
        e.setStatus(PostComposeAiSnapshotStatus.REVERTED);

        when(snapshotsRepository.findByIdAndUserId(eq(2L), eq(10L))).thenReturn(Optional.of(e));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotDTO dto = svc.revert(2L);
        assertEquals(PostComposeAiSnapshotStatus.REVERTED, dto.getStatus());
        verify(snapshotsRepository, never()).save(any());
    }

    @Test
    void revert_pending_updatesAndSaves() {
        mockAuth(mockAuthenticatedEmail("u@example.com"));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        PostDraftsRepository postDraftsRepository = mock(PostDraftsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(userWithId(10L)));

        PostComposeAiSnapshotsEntity e = new PostComposeAiSnapshotsEntity();
        e.setId(3L);
        e.setUserId(10L);
        e.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
        e.setBeforeTitle("t");
        e.setBeforeContent("c");
        e.setBeforeBoardId(10L);
        e.setStatus(PostComposeAiSnapshotStatus.PENDING);

        when(snapshotsRepository.findByIdAndUserId(eq(3L), eq(10L))).thenReturn(Optional.of(e));
        when(snapshotsRepository.save(any(PostComposeAiSnapshotsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostComposeAiSnapshotsServiceImpl svc = newService(snapshotsRepository, postDraftsRepository, postsRepository, administratorService);

        PostComposeAiSnapshotDTO dto = svc.revert(3L);
        assertEquals(PostComposeAiSnapshotStatus.REVERTED, dto.getStatus());
        assertNotNull(dto.getResolvedAt());
        verify(snapshotsRepository).save(eq(e));
    }
}

