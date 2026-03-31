package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesCreateDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRulesRepository;
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

public class AdminModerationRulesServiceAuditTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());
        when(repo.save(any())).thenAnswer(inv -> {
            ModerationRulesEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);
        ModerationRulesCreateDTO dto = new ModerationRulesCreateDTO();
        dto.setName("r1");
        dto.setType(RuleType.REGEX);
        dto.setPattern("bad");
        dto.setSeverity(Severity.HIGH);
        dto.setEnabled(true);

        svc.create(dto);

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("MODERATION_RULE_CREATE"),
                eq("MODERATION_RULE"),
                eq(10L),
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

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        ModerationRulesEntity existing = new ModerationRulesEntity();
        existing.setId(11L);
        existing.setName("r1");
        existing.setType(RuleType.REGEX);
        existing.setPattern("bad");
        existing.setEnabled(true);

        when(repo.findById(11L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);
        ModerationRulesUpdateDTO dto = new ModerationRulesUpdateDTO();
        dto.setEnabled(false);

        svc.update(11L, dto);

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("MODERATION_RULE_UPDATE"),
                eq("MODERATION_RULE"),
                eq(11L),
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

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        ModerationRulesEntity existing = new ModerationRulesEntity();
        existing.setId(12L);
        existing.setName("r1");

        when(repo.findById(12L)).thenReturn(Optional.of(existing));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);

        svc.delete(12L);

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("MODERATION_RULE_DELETE"),
                eq("MODERATION_RULE"),
                eq(12L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}
