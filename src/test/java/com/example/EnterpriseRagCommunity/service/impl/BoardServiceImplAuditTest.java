package com.example.EnterpriseRagCommunity.service.impl;

import com.example.EnterpriseRagCommunity.dto.content.BoardsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BoardServiceImplAuditTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        BoardsRepository repo = mock(BoardsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());
        when(repo.save(any())).thenAnswer(inv -> {
            BoardsEntity e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });

        BoardServiceImpl svc = new BoardServiceImpl(repo, auditLogWriter, auditDiffBuilder);
        BoardsCreateDTO dto = new BoardsCreateDTO();
        dto.setName("b1");
        dto.setVisible(true);
        dto.setSortOrder(1);

        svc.createBoard(dto);

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("BOARD_CREATE"),
                eq("BOARD"),
                eq(101L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void updateWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        BoardsRepository repo = mock(BoardsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        BoardsEntity existing = new BoardsEntity();
        existing.setId(102L);
        existing.setName("b1");
        when(repo.findById(102L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        BoardServiceImpl svc = new BoardServiceImpl(repo, auditLogWriter, auditDiffBuilder);
        BoardsUpdateDTO dto = new BoardsUpdateDTO();
        dto.setId(102L);
        dto.setName(java.util.Optional.of("b2"));

        svc.updateBoard(dto);

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("BOARD_UPDATE"),
                eq("BOARD"),
                eq(102L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void deleteWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        BoardsRepository repo = mock(BoardsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        BoardsEntity existing = new BoardsEntity();
        existing.setId(103L);
        existing.setName("b1");
        when(repo.findById(103L)).thenReturn(Optional.of(existing));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        BoardServiceImpl svc = new BoardServiceImpl(repo, auditLogWriter, auditDiffBuilder);
        svc.deleteBoard(103L);

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("BOARD_DELETE"),
                eq("BOARD"),
                eq(103L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}

