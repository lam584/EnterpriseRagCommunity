package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.AccessLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

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
import static org.mockito.Mockito.never;
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
        when(repo.findFirstByRequestIdOrderByIdDesc("rid")).thenReturn(Optional.of(e));
        when(repo.findById(8L)).thenReturn(Optional.empty());
        when(repo.findFirstByRequestIdOrderByIdDesc("8")).thenReturn(Optional.empty());
        when(repo.findFirstByTraceIdOrderByIdDesc("8")).thenReturn(Optional.empty());

        AccessLogsViewDTO dto = svc.getById("3");
        assertEquals(3L, dto.id());
        assertEquals("GET", dto.method());
        assertEquals("rid", dto.requestId());
        assertFalse(dto.details().isEmpty());
        assertEquals(3L, svc.getById("rid").id());

        assertThrows(NoSuchElementException.class, () -> svc.getById("8"));
    }

    @Test
    void query_should_not_include_details_but_getById_should() {
        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogsService svc = new AccessLogsService(repo);

        AccessLogsEntity entity = new AccessLogsEntity();
        entity.setId(11L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setMethod("GET");
        entity.setPath("/api/demo");
        entity.setDetails(Map.of("reqBody", Map.of("body", "payload")));

        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(repo.findById(11L)).thenReturn(Optional.of(entity));

        Page<AccessLogsViewDTO> page = svc.query(1, 20, null, null, null, null, null, null, null, null, null, null, null, null);
        AccessLogsViewDTO listItem = page.getContent().get(0);
        assertEquals(null, listItem.details());

        AccessLogsViewDTO detailItem = svc.getById("11");
        assertNotNull(detailItem.details());
        assertFalse(detailItem.details().isEmpty());
    }

    @Test
    void query_should_read_from_es_when_sink_mode_is_kafka() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                        {
                            "hits": {
                                "total": {"value": 1},
                                "hits": [
                                    {
                                        "_source": {
                                            "event_id": "evt-1",
                                            "created_at": "2026-04-17T10:10:10",
                                            "user_id": 9,
                                            "username": "alice",
                                            "method": "GET",
                                            "path": "/api/demo",
                                            "status_code": 200,
                                            "client_ip": "127.0.0.1",
                                            "request_id": "req-1",
                                            "trace_id": "trace-1"
                                        }
                                    }
                                ]
                            }
                        }
                        """);

        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogsService svc = kafkaService(repo);

        Page<AccessLogsViewDTO> out = svc.query(1, 20, null, null, null, null, null, null, null, null, null, null, null, "createdAt,desc");
        assertEquals(1, out.getTotalElements());
        assertEquals(1, out.getContent().size());
        AccessLogsViewDTO first = out.getContent().getFirst();
        assertEquals("alice", first.username());
        assertEquals("GET", first.method());
        assertEquals("/api/demo", first.path());
        assertEquals("req-1", first.requestId());

        verify(repo, never()).findAll(any(Specification.class), any(Pageable.class));

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertTrue(req.url().toString().contains("/access-logs-v1/_search"));
        assertEquals("POST", req.method());
        String body = new String(req.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"track_total_hits\":true"));
        assertTrue(body.contains("\"event_id.keyword\""));
        assertFalse(body.contains("\"event_id\":{\"order\""));
    }

    @Test
    void getById_should_read_detail_from_es_when_sink_mode_is_kafka() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                        {
                            "hits": {
                                "hits": [
                                    {
                                        "_source": {
                                            "event_id": "req-1",
                                            "created_at": "2026-04-17T10:10:10",
                                            "user_id": 9,
                                            "username": "alice",
                                            "method": "GET",
                                            "path": "/api/demo",
                                            "status_code": 200,
                                            "client_ip": "127.0.0.1",
                                            "request_id": "req-1",
                                            "trace_id": "trace-1",
                                            "details": {
                                                "reqBody": {"body": "payload"}
                                            }
                                        }
                                    }
                                ]
                            }
                        }
                        """);

        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogsService svc = kafkaService(repo);

        AccessLogsViewDTO dto = svc.getById("req-1");
        assertEquals("alice", dto.username());
        assertEquals("req-1", dto.requestId());
        assertNotNull(dto.details());
        assertEquals("payload", ((Map<?, ?>) dto.details().get("reqBody")).get("body"));

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        String body = new String(req.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"request_id\":\"req-1\""));
        assertTrue(body.contains("\"trace_id\":\"req-1\""));
    }

    private static AccessLogsService kafkaService(AccessLogsRepository repo) {
        AccessLogsService svc = new AccessLogsService(repo);
        SystemConfigurationService configService = mock(SystemConfigurationService.class);
        when(configService.getConfig("app.logging.access.sink-mode")).thenReturn("KAFKA");
        when(configService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(configService.getConfig("app.logging.access.es-sink.index")).thenReturn("access-logs-v1");
        when(configService.getConfig("APP_ES_API_KEY")).thenReturn("");
        ReflectionTestUtils.setField(svc, "systemConfigurationService", configService);
        return svc;
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
