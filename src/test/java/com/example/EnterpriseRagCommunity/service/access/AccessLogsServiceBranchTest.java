package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.AccessLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
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

class AccessLogsServiceBranchTest {

    @Test
    void query_should_cover_filters_and_sort_branches() {
        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        AccessLogsService svc = new AccessLogsService(repo);

        svc.query(0, 30000, "  kw ", 9L, " user ", " GET ", " /p ", 200, " 127 ", " req ", " tr ",
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), "createdAt,asc");
        svc.query(null, null, null, null, null, null, null, null, null, null, null, null, null, "badField,badDir");
        svc.query(1, 1, null, null, null, null, null, null, null, null, null, null, null, " ,desc");
        svc.query(1, 1, null, null, null, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Specification<AccessLogsEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(repo, atLeastOnce()).findAll(specCaptor.capture(), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertTrue(pageable.getPageNumber() >= 0);
        assertTrue(pageable.getPageSize() >= 1);
        assertNotNull(pageable.getSort().getOrderFor("id"));

        for (Specification<AccessLogsEntity> spec : specCaptor.getAllValues()) {
            CriteriaEnv env = new CriteriaEnv();
            Predicate out = spec.toPredicate(env.root, env.query, env.cb);
            assertNotNull(out);
        }
    }

    @Test
    void getById_should_map_and_throw_when_missing() {
        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogsService svc = new AccessLogsService(repo);

        AccessLogsEntity e = new AccessLogsEntity();
        e.setId(3L);
        e.setCreatedAt(LocalDateTime.now());
        e.setTenantId(1L);
        e.setUserId(2L);
        e.setUsername("u");
        e.setMethod("GET");
        e.setPath("/p");
        e.setQueryString("a=b");
        e.setStatusCode(200);
        e.setLatencyMs(10);
        e.setClientIp("127.0.0.1");
        e.setClientPort(1);
        e.setServerIp("10.0.0.1");
        e.setServerPort(2);
        e.setScheme("http");
        e.setHost("h");
        e.setRequestId("rid");
        e.setTraceId("tid");
        e.setUserAgent("ua");
        e.setReferer("ref");
        e.setDetails(Map.of("k", "v"));
        when(repo.findById(3L)).thenReturn(Optional.of(e));
        when(repo.findById(8L)).thenReturn(Optional.empty());

        AccessLogsViewDTO dto = svc.getById(3L);
        assertEquals(3L, dto.id());
        assertEquals("GET", dto.method());
        assertEquals("rid", dto.requestId());
        assertFalse(dto.details().isEmpty());

        assertThrows(NoSuchElementException.class, () -> svc.getById(8L));
    }

    private static final class CriteriaEnv {
        private final Root<AccessLogsEntity> root = mock(Root.class);
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
            lenient().when(cb.between(any(Expression.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(p);
            lenient().when(cb.or(any(Predicate[].class))).thenReturn(p);
            lenient().when(cb.and(any(Predicate[].class))).thenReturn(p);
        }
    }
}
