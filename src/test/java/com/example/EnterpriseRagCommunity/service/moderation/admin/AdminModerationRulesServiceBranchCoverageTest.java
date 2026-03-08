package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesCreateDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRulesRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminModerationRulesServiceBranchCoverageTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listShouldNormalizePagingFilterByCategoryAndRunSpecificationBranches() {
        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AdminModerationRulesService svc = new AdminModerationRulesService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        ModerationRulesEntity keep = rule(1L, "rule-1", RuleType.REGEX, Severity.HIGH, true, Map.of("category", "spam"));
        ModerationRulesEntity dropDifferentCategory = rule(2L, "rule-2", RuleType.REGEX, Severity.HIGH, true, Map.of("category", "safe"));
        ModerationRulesEntity dropNullMetadata = rule(3L, "rule-3", RuleType.REGEX, Severity.HIGH, true, null);
        Page<ModerationRulesEntity> page = new PageImpl<>(List.of(keep, dropDifferentCategory, dropNullMetadata), PageRequest.of(0, 200), 3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<ModerationRulesEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.findAll(specCaptor.capture(), pageableCaptor.capture())).thenReturn(page);

        Page<ModerationRulesEntity> out = svc.list(0, 500, " bad ", RuleType.REGEX, Severity.HIGH, true, "spam");

        assertEquals(1, out.getTotalElements());
        assertEquals(1, out.getContent().size());
        assertEquals(1L, out.getContent().get(0).getId());
        Pageable used = pageableCaptor.getValue();
        assertEquals(0, used.getPageNumber());
        assertEquals(200, used.getPageSize());
        assertEquals("DESC", used.getSort().getOrderFor("id").getDirection().name());

        CriteriaEnv env = new CriteriaEnv();
        specCaptor.getValue().toPredicate(env.root, env.criteriaQuery, env.cb);
    }

    @Test
    void listShouldReturnRawPageWhenCategoryBlank() {
        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AdminModerationRulesService svc = new AdminModerationRulesService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        Page<ModerationRulesEntity> page = new PageImpl<>(List.of(rule(10L, "r", RuleType.KEYWORD, Severity.LOW, true, null)));
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ModerationRulesEntity> out = svc.list(2, 0, null, null, null, null, " ");
        assertSame(page, out);
    }

    @Test
    void createShouldUseDefaultEnabledWhenDtoEnabledNullAndAnonymousUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("anonymousUser", "N/A", List.of())
        );

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        when(repo.save(any())).thenAnswer(inv -> {
            ModerationRulesEntity e = inv.getArgument(0);
            e.setId(88L);
            return e;
        });

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);
        ModerationRulesCreateDTO dto = new ModerationRulesCreateDTO();
        dto.setName("new-rule");
        dto.setType(RuleType.KEYWORD);
        dto.setPattern("bad");
        dto.setSeverity(Severity.MEDIUM);
        dto.setEnabled(null);

        ModerationRulesEntity out = svc.create(dto);

        assertEquals(88L, out.getId());
        assertEquals(Boolean.TRUE, out.getEnabled());
        verify(auditLogWriter).write(
                eq(null),
                eq(null),
                eq("MODERATION_RULE_CREATE"),
                eq("MODERATION_RULE"),
                eq(88L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void createShouldUseTrimmedUsernameAndExplicitEnabledValue() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("principal");
        when(auth.getName()).thenReturn("  bob@example.com  ");
        SecurityContextHolder.getContext().setAuthentication(auth);

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        when(repo.save(any())).thenAnswer(inv -> {
            ModerationRulesEntity e = inv.getArgument(0);
            e.setId(89L);
            return e;
        });

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);
        ModerationRulesCreateDTO dto = new ModerationRulesCreateDTO();
        dto.setName("explicit-enabled");
        dto.setType(RuleType.REGEX);
        dto.setPattern("x");
        dto.setSeverity(Severity.LOW);
        dto.setEnabled(Boolean.FALSE);

        ModerationRulesEntity out = svc.create(dto);

        assertEquals(89L, out.getId());
        assertEquals(Boolean.FALSE, out.getEnabled());
        verify(auditLogWriter).write(
                eq(null),
                eq("bob@example.com"),
                eq("MODERATION_RULE_CREATE"),
                eq("MODERATION_RULE"),
                eq(89L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void createShouldWriteNullUsernameWhenAuthenticationMissing() {
        SecurityContextHolder.clearContext();

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        when(repo.save(any())).thenAnswer(inv -> {
            ModerationRulesEntity e = inv.getArgument(0);
            e.setId(90L);
            return e;
        });

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);
        ModerationRulesCreateDTO dto = new ModerationRulesCreateDTO();
        dto.setName("no-auth");
        dto.setType(RuleType.KEYWORD);
        dto.setPattern("x");
        dto.setSeverity(Severity.MEDIUM);
        dto.setEnabled(Boolean.TRUE);

        svc.create(dto);

        verify(auditLogWriter).write(
                eq(null),
                eq(null),
                eq("MODERATION_RULE_CREATE"),
                eq("MODERATION_RULE"),
                eq(90L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void updateShouldApplyAllOptionalFieldsAndHandleAuthNameException() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("user-principal");
        when(auth.getName()).thenThrow(new RuntimeException("boom"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        ModerationRulesEntity existing = rule(12L, "old", RuleType.REGEX, Severity.LOW, true, new HashMap<>());
        when(repo.findById(12L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);
        ModerationRulesUpdateDTO dto = new ModerationRulesUpdateDTO();
        dto.setName(Optional.of("new-name"));
        dto.setType(Optional.of(RuleType.PATTERN));
        dto.setPattern(Optional.of("p2"));
        dto.setSeverity(Optional.of(Severity.HIGH));
        dto.setEnabled(Optional.of(false));
        dto.setMetadata(Optional.of(Map.of("category", "updated")));

        ModerationRulesEntity out = svc.update(12L, dto);

        assertEquals("new-name", out.getName());
        assertEquals(RuleType.PATTERN, out.getType());
        assertEquals("p2", out.getPattern());
        assertEquals(Severity.HIGH, out.getSeverity());
        assertEquals(Boolean.FALSE, out.getEnabled());
        assertEquals("updated", out.getMetadata().get("category"));
        verify(auditLogWriter).write(
                eq(null),
                eq(null),
                eq("MODERATION_RULE_UPDATE"),
                eq("MODERATION_RULE"),
                eq(12L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void updateShouldKeepValuesWhenOptionalsAreEmptyAndUsernameBlank() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("principal");
        when(auth.getName()).thenReturn("   ");
        SecurityContextHolder.getContext().setAuthentication(auth);

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        ModerationRulesEntity existing = rule(13L, "origin", RuleType.KEYWORD, Severity.MEDIUM, true, new HashMap<>());
        when(repo.findById(13L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);
        ModerationRulesUpdateDTO dto = new ModerationRulesUpdateDTO();
        dto.setName(Optional.empty());
        dto.setType(Optional.empty());
        dto.setPattern(Optional.empty());
        dto.setSeverity(Optional.empty());
        dto.setEnabled(Optional.empty());
        dto.setMetadata(Optional.empty());

        ModerationRulesEntity out = svc.update(13L, dto);

        assertEquals("origin", out.getName());
        assertEquals(RuleType.KEYWORD, out.getType());
        assertEquals("p-13", out.getPattern());
        assertEquals(Severity.MEDIUM, out.getSeverity());
        assertEquals(Boolean.TRUE, out.getEnabled());
        verify(auditLogWriter).write(
                eq(null),
                eq(null),
                eq("MODERATION_RULE_UPDATE"),
                eq("MODERATION_RULE"),
                eq(13L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void updateShouldThrowWhenRuleNotFound() {
        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        when(repo.findById(999L)).thenReturn(Optional.empty());
        AdminModerationRulesService svc = new AdminModerationRulesService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.update(999L, new ModerationRulesUpdateDTO()));
        assertTrue(ex.getMessage().contains("规则不存在"));
    }

    @Test
    void deleteShouldSucceedWhenAuthenticationIsNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(auth.getPrincipal()).thenReturn("principal");
        SecurityContextHolder.getContext().setAuthentication(auth);

        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        ModerationRulesEntity existing = rule(14L, "to-del", RuleType.PATTERN, Severity.HIGH, true, Map.of());
        when(repo.findById(14L)).thenReturn(Optional.of(existing));

        AdminModerationRulesService svc = new AdminModerationRulesService(repo, auditLogWriter, auditDiffBuilder);
        svc.delete(14L);

        verify(repo).deleteById(14L);
        verify(auditLogWriter).write(
                eq(null),
                eq(null),
                eq("MODERATION_RULE_DELETE"),
                eq("MODERATION_RULE"),
                eq(14L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void deleteShouldThrowWhenRuleNotFound() {
        ModerationRulesRepository repo = mock(ModerationRulesRepository.class);
        when(repo.findById(1000L)).thenReturn(Optional.empty());
        AdminModerationRulesService svc = new AdminModerationRulesService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.delete(1000L));
        assertTrue(ex.getMessage().contains("规则不存在"));
    }

    @Test
    void summarizeShouldReturnEmptyMapWhenEntityNull() throws Exception {
        Method summarize = AdminModerationRulesService.class.getDeclaredMethod("summarize", ModerationRulesEntity.class);
        summarize.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) summarize.invoke(null, new Object[]{null});
        assertTrue(out.isEmpty());
    }

    private static ModerationRulesEntity rule(Long id, String name, RuleType type, Severity severity, Boolean enabled, Map<String, Object> metadata) {
        ModerationRulesEntity e = new ModerationRulesEntity();
        e.setId(id);
        e.setName(name);
        e.setType(type);
        e.setPattern("p-" + id);
        e.setSeverity(severity);
        e.setEnabled(enabled);
        e.setMetadata(metadata);
        return e;
    }

    private static final class CriteriaEnv {
        private final Root<ModerationRulesEntity> root = mock(Root.class);
        private final CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

        private final Path<String> namePath = mock(Path.class);
        private final Path<String> patternPath = mock(Path.class);
        private final Path<RuleType> typePath = mock(Path.class);
        private final Path<Severity> severityPath = mock(Path.class);
        private final Path<Boolean> enabledPath = mock(Path.class);

        private CriteriaEnv() {
            lenient().when(root.<String>get("name")).thenReturn(namePath);
            lenient().when(root.<String>get("pattern")).thenReturn(patternPath);
            lenient().when(root.<RuleType>get("type")).thenReturn(typePath);
            lenient().when(root.<Severity>get("severity")).thenReturn(severityPath);
            lenient().when(root.<Boolean>get("enabled")).thenReturn(enabledPath);

            lenient().when(cb.like(any(Expression.class), anyString())).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.equal(any(), any())).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.or(any(Predicate[].class))).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.and(any(Predicate[].class))).thenAnswer(inv -> mock(Predicate.class));
        }
    }
}
