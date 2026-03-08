package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostDraftsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostDraftsEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostDraftsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PostDraftsServiceImplAuditTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listMineThrowsWhenNotLoggedIn() {
        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        PostDraftsServiceImpl svc = newService(repo, administratorService, auditLogWriter, auditDiffBuilder);

        AuthenticationException ex = assertThrows(AuthenticationException.class, () -> svc.listMine(Pageable.unpaged()));
        assertEquals("未登录或会话已过期", ex.getMessage());
        verifyNoInteractions(administratorService, repo);
    }

    @Test
    void listMineThrowsWhenNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("alice@example.com", "N/A"));

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        PostDraftsServiceImpl svc = newService(repo, administratorService, auditLogWriter, auditDiffBuilder);

        AuthenticationException ex = assertThrows(AuthenticationException.class, () -> svc.listMine(Pageable.unpaged()));
        assertEquals("未登录或会话已过期", ex.getMessage());
        verifyNoInteractions(administratorService, repo);
    }

    @Test
    void listMineThrowsWhenAnonymousUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        );

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        PostDraftsServiceImpl svc = newService(repo, administratorService, auditLogWriter, auditDiffBuilder);

        AuthenticationException ex = assertThrows(AuthenticationException.class, () -> svc.listMine(Pageable.unpaged()));
        assertEquals("未登录或会话已过期", ex.getMessage());
        verifyNoInteractions(administratorService, repo);
    }

    @Test
    void listMineThrowsWhenCurrentUserMissing() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        when(administratorService.findByUsername("ghost@example.com")).thenReturn(Optional.empty());

        PostDraftsServiceImpl svc = newService(repo, administratorService, auditLogWriter, auditDiffBuilder);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.listMine(Pageable.unpaged()));
        assertEquals("当前用户不存在", ex.getMessage());
        verifyNoInteractions(repo);
    }

    @Test
    void createWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());
        when(repo.save(any())).thenAnswer(inv -> {
            PostDraftsEntity e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });

        PostDraftsServiceImpl svc = new PostDraftsServiceImpl();
        ReflectionTestUtils.setField(svc, "postDraftsRepository", repo);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(svc, "auditDiffBuilder", auditDiffBuilder);

        PostDraftsCreateDTO dto = new PostDraftsCreateDTO();
        dto.setTenantId(1L);
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");

        svc.create(dto);

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("POST_DRAFT_CREATE"),
                eq("POST_DRAFT"),
                eq(100L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void createNormalizesNullTitleAndContent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));
        doNothing().when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        when(repo.save(any())).thenAnswer(inv -> {
            PostDraftsEntity e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });

        PostDraftsServiceImpl svc = newService(repo, administratorService, auditLogWriter, auditDiffBuilder);

        PostDraftsCreateDTO dto = new PostDraftsCreateDTO();
        dto.setTenantId(1L);
        dto.setBoardId(2L);
        dto.setTitle(null);
        dto.setContent(null);

        svc.create(dto);

        var captor = org.mockito.ArgumentCaptor.forClass(PostDraftsEntity.class);
        verify(repo).save(captor.capture());
        PostDraftsEntity saved = captor.getValue();
        assertEquals("", saved.getTitle());
        assertEquals("", saved.getContent());
    }

    @Test
    void updateWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        PostDraftsEntity existing = new PostDraftsEntity();
        existing.setId(101L);
        existing.setAuthorId(10L);
        existing.setTitle("t");
        existing.setContent("c");
        when(repo.findByIdAndAuthorId(101L, 10L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostDraftsServiceImpl svc = new PostDraftsServiceImpl();
        ReflectionTestUtils.setField(svc, "postDraftsRepository", repo);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(svc, "auditDiffBuilder", auditDiffBuilder);

        PostDraftsUpdateDTO dto = new PostDraftsUpdateDTO();
        dto.setBoardId(3L);
        dto.setTitle("t2");
        dto.setContent("c2");

        svc.updateMine(101L, dto);

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("POST_DRAFT_UPDATE"),
                eq("POST_DRAFT"),
                eq(101L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void updateNormalizesNullTitleAndContent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));
        doNothing().when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        PostDraftsEntity existing = new PostDraftsEntity();
        existing.setId(201L);
        existing.setAuthorId(10L);
        existing.setTitle("t");
        existing.setContent("c");
        when(repo.findByIdAndAuthorId(201L, 10L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostDraftsServiceImpl svc = newService(repo, administratorService, auditLogWriter, auditDiffBuilder);

        PostDraftsUpdateDTO dto = new PostDraftsUpdateDTO();
        dto.setBoardId(3L);
        dto.setTitle(null);
        dto.setContent(null);

        svc.updateMine(201L, dto);

        var captor = org.mockito.ArgumentCaptor.forClass(PostDraftsEntity.class);
        verify(repo).save(captor.capture());
        PostDraftsEntity saved = captor.getValue();
        assertEquals("", saved.getTitle());
        assertEquals("", saved.getContent());
    }

    @Test
    void updateCoversSummarizeForAuditWithMetadataMapAndNullFields() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("user");
        when(auth.getName()).thenReturn("alice@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));
        doNothing().when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        var beforeCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        when(auditDiffBuilder.build(beforeCaptor.capture(), any())).thenReturn(Map.of());

        PostDraftsEntity existing = new PostDraftsEntity();
        existing.setId(301L);
        existing.setAuthorId(10L);
        existing.setTitle(null);
        existing.setContent(null);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put(null, 1);
        meta.put("k1", "v1");
        meta.put("k2", 2);
        existing.setMetadata(meta);

        when(repo.findByIdAndAuthorId(301L, 10L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostDraftsServiceImpl svc = newService(repo, administratorService, auditLogWriter, auditDiffBuilder);

        PostDraftsUpdateDTO dto = new PostDraftsUpdateDTO();
        dto.setBoardId(3L);
        dto.setTitle("t2");
        dto.setContent("c2");
        dto.setMetadata(meta);

        svc.updateMine(301L, dto);

        Map<?, ?> before = beforeCaptor.getValue();
        assertNotNull(before);
        assertEquals(0, before.get("titleLen"));
        assertEquals(0, before.get("contentLen"));
        assertEquals(3, before.get("metadataKeyCount"));
        assertNotNull(before.get("metadataKeys"));
        assertEquals(2, ((List<?>) before.get("metadataKeys")).size());
    }

    @Test
    void summarizeForAuditReturnsEmptyWhenEntityIsNull() throws Exception {
        var m = PostDraftsServiceImpl.class.getDeclaredMethod("summarizeForAudit", PostDraftsEntity.class);
        m.setAccessible(true);
        Object r = m.invoke(null, new Object[]{null});
        assertNotNull(r);
        assertEquals(Map.of(), r);
    }

    @Test
    void currentUsernameOrNullCoversDifferentAuthAndNameBranches() throws Exception {
        var m = PostDraftsServiceImpl.class.getDeclaredMethod("currentUsernameOrNull");
        m.setAccessible(true);

        SecurityContextHolder.clearContext();
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("alice@example.com", "N/A"));
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        );
        assertNull(m.invoke(null));

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("user");
        when(auth.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertNull(m.invoke(null));

        Authentication auth2 = mock(Authentication.class);
        when(auth2.isAuthenticated()).thenReturn(true);
        when(auth2.getPrincipal()).thenReturn("user");
        when(auth2.getName()).thenReturn("   ");
        SecurityContextHolder.getContext().setAuthentication(auth2);
        assertNull(m.invoke(null));

        Authentication auth3 = mock(Authentication.class);
        when(auth3.isAuthenticated()).thenReturn(true);
        when(auth3.getPrincipal()).thenReturn("user");
        when(auth3.getName()).thenThrow(new RuntimeException("boom"));
        SecurityContextHolder.getContext().setAuthentication(auth3);
        assertNull(m.invoke(null));
    }

    @Test
    void deleteWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        PostDraftsRepository repo = mock(PostDraftsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        PostDraftsEntity existing = new PostDraftsEntity();
        existing.setId(102L);
        existing.setAuthorId(10L);
        existing.setTitle("t");
        existing.setContent("c");
        when(repo.findByIdAndAuthorId(102L, 10L)).thenReturn(Optional.of(existing));

        PostDraftsServiceImpl svc = new PostDraftsServiceImpl();
        ReflectionTestUtils.setField(svc, "postDraftsRepository", repo);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(svc, "auditDiffBuilder", auditDiffBuilder);

        svc.deleteMine(102L);

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("POST_DRAFT_DELETE"),
                eq("POST_DRAFT"),
                eq(102L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    private static PostDraftsServiceImpl newService(PostDraftsRepository repo, AdministratorService administratorService, AuditLogWriter auditLogWriter, AuditDiffBuilder auditDiffBuilder) {
        PostDraftsServiceImpl svc = new PostDraftsServiceImpl();
        ReflectionTestUtils.setField(svc, "postDraftsRepository", repo);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(svc, "auditDiffBuilder", auditDiffBuilder);
        return svc;
    }
}
