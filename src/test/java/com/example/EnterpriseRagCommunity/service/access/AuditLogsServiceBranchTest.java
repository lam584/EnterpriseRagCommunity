package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.AuditLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogsServiceBranchTest {

    @Test
    void query_should_cover_sort_and_op_branches() {
        AuditLogsRepository repo = mock(AuditLogsRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        AuditLogsService svc = new AuditLogsService(repo);

        svc.query(0, 50000, " kw ", 7L, " actor ", " CRUD ", " CREATE ", " USERS ", 9L, AuditResult.SUCCESS,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), " trace ", "createdAt,asc");
        svc.query(1, 1, null, null, null, null, "update", null, null, null,
                null, null, null, "id,desc");
        svc.query(1, 1, null, null, null, null, "delete", null, null, null,
                null, null, null, "id,bad");
        svc.query(1, 1, null, null, null, null, "unknown", null, null, null,
                null, null, null, ",");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Specification<AuditLogsEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(repo, atLeastOnce()).findAll(specCaptor.capture(), pageableCaptor.capture());
        assertTrue(pageableCaptor.getValue().getPageNumber() >= 0);
        assertTrue(pageableCaptor.getValue().getPageSize() >= 1);
        assertNotNull(pageableCaptor.getValue().getSort().getOrderFor("id"));

        for (Specification<AuditLogsEntity> spec : specCaptor.getAllValues()) {
            CriteriaEnv env = new CriteriaEnv();
            Predicate out = spec.toPredicate(env.root, env.query, env.cb);
            assertNotNull(out);
        }
    }

    @Test
    void getById_should_map_boolean_and_throw_when_missing() {
        AuditLogsRepository repo = mock(AuditLogsRepository.class);
        AuditLogsService svc = new AuditLogsService(repo);

        AuditLogsEntity e = new AuditLogsEntity();
        e.setId(2L);
        e.setCreatedAt(LocalDateTime.now());
        e.setTenantId(1L);
        e.setActorUserId(8L);
        e.setAction("CRUD_CREATE");
        e.setEntityType("USERS");
        e.setEntityId(9L);
        e.setResult(AuditResult.SUCCESS);
        e.setDetails(Map.of(
                "actorName", "alice",
                "message", "ok",
                "ip", "127",
                "traceId", "tr",
                "method", "POST",
                "path", "/x",
                "autoCrud", "true"
        ));
        when(repo.findById(2L)).thenReturn(Optional.of(e));
        AuditLogsEntity e2 = new AuditLogsEntity();
        e2.setId(3L);
        e2.setCreatedAt(LocalDateTime.now());
        e2.setAction("CRUD_UPDATE");
        e2.setEntityType("USERS");
        e2.setResult(AuditResult.SUCCESS);
        e2.setDetails(Map.of("autoCrud", "false"));
        when(repo.findById(3L)).thenReturn(Optional.of(e2));
        when(repo.findById(99L)).thenReturn(Optional.empty());

        AuditLogsViewDTO dto = svc.getById(2L);
        assertEquals("alice", dto.actorName());
        assertEquals("ok", dto.message());
        assertEquals(Boolean.TRUE, dto.autoCrud());
        assertFalse(dto.details().isEmpty());
        assertEquals(Boolean.FALSE, svc.getById(3L).autoCrud());

        assertThrows(NoSuchElementException.class, () -> svc.getById(99L));
    }

    private static final class CriteriaEnv {
        private final Root<AuditLogsEntity> root = mock(Root.class);
        private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
        private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

        private final Path<Object> anyPath = mock(Path.class);
        private final Predicate p = mock(Predicate.class);

        private CriteriaEnv() {
            lenient().when(root.get(anyString())).thenReturn(anyPath);
            lenient().when(anyPath.as(any())).thenReturn((Expression) anyPath);
            lenient().when(cb.isNull(any())).thenReturn(p);
            lenient().when(cb.equal(any(), any())).thenReturn(p);
            lenient().when(cb.like(any(), anyString())).thenReturn(p);
            lenient().when(cb.conjunction()).thenReturn(p);
            lenient().when(cb.between(any(Expression.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(p);
            lenient().when(cb.or(any(Predicate[].class))).thenReturn(p);
            lenient().when(cb.and(any(Predicate[].class))).thenReturn(p);
        }
    }
}
