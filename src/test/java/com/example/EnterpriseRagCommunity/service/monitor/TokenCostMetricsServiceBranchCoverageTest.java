package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenMetricsResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenTimelineResponseDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenCostMetricsServiceBranchCoverageTest {

    @Test
    void queryShouldSwapTimeAndRouteSources() {
        EntityManager em = mock(EntityManager.class);
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);

        List<String> sqls = new ArrayList<>();
        when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);
            sqls.add(sql);
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            List<Object[]> rows;
            if (sql.contains("FROM generation_jobs")) {
                rows = java.util.Collections.singletonList(new Object[]{"jobModel", 17L, 0L});
            } else if (sql.contains("h.type IN ('TEXT_CHAT'")) {
                rows = java.util.Collections.singletonList(new Object[]{"chatModel", 11L, 0L});
            } else if (sql.contains("h.type IN ('TEXT_MODERATION'")) {
                rows = java.util.Collections.singletonList(new Object[]{"moderationModel", 13L, 0L});
            } else if (sql.contains("h.type = :taskType")) {
                rows = java.util.Collections.singletonList(new Object[]{"taskModel", 7L, 0L});
            } else {
                rows = java.util.Collections.singletonList(new Object[]{"allModel", 19L, 0L});
            }
            when(q.getResultList()).thenReturn(rows);
            return q;
        });
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), anyCollection()))
                .thenReturn(List.of());
        when(priceRepo.findByNameIn(anyCollection())).thenReturn(List.of());

        TokenCostMetricsService svc = new TokenCostMetricsService(modelRepo, priceRepo);
        ReflectionTestUtils.setField(svc, "entityManager", em);

        LocalDateTime t1 = LocalDateTime.of(2020, 1, 2, 0, 0);
        LocalDateTime t0 = LocalDateTime.of(2020, 1, 1, 0, 0);

        AdminTokenMetricsResponseDTO all = svc.query(t1, t0, "ALL", null);
        assertNotNull(all);
        assertTrue(!all.getStart().isAfter(all.getEnd()));
        assertEquals(19L, all.getTotalTokens());

        AdminTokenMetricsResponseDTO tt = svc.query(t0, t1, "SOME_TASK_TYPE", null);
        assertEquals(7L, tt.getTotalTokens());

        AdminTokenMetricsResponseDTO chat = svc.query(t0, t1, "CHAT", null);
        assertEquals(11L, chat.getTotalTokens());

        AdminTokenMetricsResponseDTO moderation = svc.query(t0, t1, "MODERATION", null);
        assertEquals(13L, moderation.getTotalTokens());

        AdminTokenMetricsResponseDTO job = svc.query(t0, t1, "JOB", null);
        assertEquals(17L, job.getTotalTokens());

        assertTrue(sqls.stream().anyMatch(s -> s.contains("h.status = 'DONE'")));
        assertTrue(sqls.stream().anyMatch(s -> s.contains("TEXT_CHAT")));
        assertTrue(sqls.stream().anyMatch(s -> s.contains("TEXT_MODERATION")));
        assertTrue(sqls.stream().anyMatch(s -> s.contains("FROM generation_jobs")));
    }

    @Test
    void queryShouldResolvePricesAndCurrencyMixed() {
        EntityManager em = mock(EntityManager.class);
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);

        when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getResultList()).thenReturn(List.of(
                    new Object[]{" modelA ", 100L, null},
                    new Object[]{"modelB", -5L, 10L},
                    new Object[]{null, 1L, 2L}
            ));
            return q;
        });

        LlmModelEntity modelA = new LlmModelEntity();
        modelA.setModelName("modelA");
        modelA.setPriceConfigId(1L);
        LlmModelEntity modelADuplicate = new LlmModelEntity();
        modelADuplicate.setModelName("modelA");
        modelADuplicate.setPriceConfigId(2L);
        LlmModelEntity modelBNoPid = new LlmModelEntity();
        modelBNoPid.setModelName("modelB");
        modelBNoPid.setPriceConfigId(null);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(eq("default"), anyCollection()))
                .thenReturn(Arrays.asList(null, modelA, modelADuplicate, modelBNoPid));

        LlmPriceConfigEntity pcNoId = new LlmPriceConfigEntity();
        pcNoId.setName("unused");
        pcNoId.setId(null);

        LlmPriceConfigEntity pcA = new LlmPriceConfigEntity();
        pcA.setId(1L);
        pcA.setName("pcA");
        pcA.setCurrency("usd");
        pcA.setInputCostPer1k(BigDecimal.ONE);
        pcA.setOutputCostPer1k(BigDecimal.valueOf(2));
        pcA.setMetadata(null);
        when(priceRepo.findByIdIn(anyCollection())).thenReturn(List.of(pcNoId, pcA));

        LlmPriceConfigEntity pcB = new LlmPriceConfigEntity();
        pcB.setId(2L);
        pcB.setName("modelB");
        pcB.setCurrency("cny");
        pcB.setMetadata(Map.of(
                "pricing", Map.of(
                        "defaultInputCostPerUnit", BigDecimal.ONE
                )
        ));

        LlmPriceConfigEntity pcUnknown = new LlmPriceConfigEntity();
        pcUnknown.setId(3L);
        pcUnknown.setName("UNKNOWN");
        pcUnknown.setCurrency("   ");
        pcUnknown.setMetadata(null);
        pcUnknown.setInputCostPer1k(null);
        pcUnknown.setOutputCostPer1k(null);

        when(priceRepo.findByNameIn(anyCollection())).thenReturn(Arrays.asList(null, pcB, pcUnknown));

        TokenCostMetricsService svc = new TokenCostMetricsService(modelRepo, priceRepo);
        ReflectionTestUtils.setField(svc, "entityManager", em);

        LocalDateTime s = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime e = LocalDateTime.of(2020, 1, 2, 0, 0);
        AdminTokenMetricsResponseDTO resp = svc.query(s, e, "ALL", LlmPricing.Mode.DEFAULT);

        assertNotNull(resp);
        assertEquals(113L, resp.getTotalTokens());
        assertEquals("MIXED", resp.getCurrency());

        assertNotNull(resp.getItems());
        assertTrue(resp.getItems().stream().anyMatch(it -> it != null && "modelA".equals(it.getModel()) && Boolean.FALSE.equals(it.getPriceMissing())));
        assertTrue(resp.getItems().stream().anyMatch(it -> it != null && "modelB".equals(it.getModel()) && Boolean.FALSE.equals(it.getPriceMissing())));
        assertTrue(resp.getItems().stream().anyMatch(it -> it != null && "UNKNOWN".equals(it.getModel()) && Boolean.TRUE.equals(it.getPriceMissing())));
    }

    @Test
    void queryTimelineShouldAutoSelectBucketAndFillPoints() {
        EntityManager em = mock(EntityManager.class);
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);

        List<String> sqls = new ArrayList<>();
        when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);
            sqls.add(sql);
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getResultList()).thenReturn(Arrays.asList(
                    new Object[]{Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 10, 10)), 1L, 2L},
                    new Object[]{Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 12, 5)), 3L, 4L},
                    new Object[]{null, 999L, 999L},
                    new Object[]{"bad", 1L, 1L},
                    null,
                    new Object[]{Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 15, 0))}
            ));
            return q;
        });

        TokenCostMetricsService svc = new TokenCostMetricsService(modelRepo, priceRepo);
        ReflectionTestUtils.setField(svc, "entityManager", em);

        LocalDateTime start = LocalDateTime.of(2020, 1, 1, 10, 15);
        AdminTokenTimelineResponseDTO h = svc.queryTimeline(start, start.plusHours(5), "JOB", null);
        assertNotNull(h);
        assertEquals("HOUR", h.getBucket());
        assertEquals(6, h.getPoints().size());
        assertEquals(10L, h.getTotalTokens());

        AdminTokenTimelineResponseDTO d = svc.queryTimeline(start, start.plusHours(100), "JOB", null);
        assertNotNull(d);
        assertEquals("DAY", d.getBucket());
        assertTrue(d.getPoints().size() >= 4);

        assertTrue(sqls.stream().anyMatch(s -> s.contains("%H:00:00")));
        assertTrue(sqls.stream().anyMatch(s -> s.contains("00:00:00")));
    }

    @Test
    void helperMethodsShouldCoverBranchesViaReflection() throws Exception {
        assertNull(TokenCostMetricsService.TimelineBucket.fromNullableString(null));
        assertNull(TokenCostMetricsService.TimelineBucket.fromNullableString("   "));
        assertEquals(TokenCostMetricsService.TimelineBucket.HOUR, TokenCostMetricsService.TimelineBucket.fromNullableString("hour"));
        assertNull(TokenCostMetricsService.TimelineBucket.fromNullableString("not-a-bucket"));

        assertNull(TokenCostMetricsService.TokenMetricsSource.fromNullableString(null));
        assertNull(TokenCostMetricsService.TokenMetricsSource.fromNullableString("   "));
        assertEquals(TokenCostMetricsService.TokenMetricsSource.JOB, TokenCostMetricsService.TokenMetricsSource.fromNullableString("job"));
        assertNull(TokenCostMetricsService.TokenMetricsSource.fromNullableString("not-a-source"));

        Object all = invokeStatic("parseSource", new Class[]{String.class}, new Object[]{null});
        assertEquals("ALL_SCENARIOS", invokeRecordAccessor(all, "kind").toString());
        assertEquals("ALL", invokeRecordAccessor(all, "source"));
        assertNull(invokeRecordAccessor(all, "taskType"));

        Object chat = invokeStatic("parseSource", new Class[]{String.class}, new Object[]{" chat "});
        assertEquals("LEGACY_CHAT", invokeRecordAccessor(chat, "kind").toString());

        Object moderation = invokeStatic("parseSource", new Class[]{String.class}, new Object[]{"MODERATION"});
        assertEquals("LEGACY_MODERATION", invokeRecordAccessor(moderation, "kind").toString());

        Object job = invokeStatic("parseSource", new Class[]{String.class}, new Object[]{"job"});
        assertEquals("LEGACY_JOB", invokeRecordAccessor(job, "kind").toString());

        Object tt = invokeStatic("parseSource", new Class[]{String.class}, new Object[]{"SOME_TASK"});
        assertEquals("TASK_TYPE", invokeRecordAccessor(tt, "kind").toString());
        assertEquals("SOME_TASK", invokeRecordAccessor(tt, "taskType"));

        assertEquals("UNKNOWN", invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{null}));
        assertEquals("UNKNOWN", invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{"   "}));
        assertEquals("gpt", invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{" gpt "}));

        assertNull(invokeStatic("normalizeCurrency", new Class[]{String.class}, new Object[]{null}));
        assertNull(invokeStatic("normalizeCurrency", new Class[]{String.class}, new Object[]{"   "}));
        assertEquals("USD", invokeStatic("normalizeCurrency", new Class[]{String.class}, new Object[]{" usd "}));

        assertNull(invokeStatic("asString", new Class[]{Object[].class, int.class}, new Object[]{null, 0}));
        assertNull(invokeStatic("asString", new Class[]{Object[].class, int.class}, new Object[]{new Object[0], 0}));
        assertNull(invokeStatic("asString", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{null}, 0}));
        assertNull(invokeStatic("asString", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{"   "}, 0}));
        assertEquals("x", invokeStatic("asString", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{" x "}, 0}));

        assertNull(invokeStatic("asLong", new Class[]{Object[].class, int.class}, new Object[]{null, 0}));
        assertNull(invokeStatic("asLong", new Class[]{Object[].class, int.class}, new Object[]{new Object[0], 0}));
        assertNull(invokeStatic("asLong", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{null}, 0}));
        assertEquals(7L, invokeStatic("asLong", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{7}, 0}));
        assertEquals(8L, invokeStatic("asLong", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{"8"}, 0}));
        assertNull(invokeStatic("asLong", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{"x"}, 0}));

        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 0, 0));
        assertEquals(ts.toLocalDateTime(), invokeStatic("asLocalDateTime", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{ts}, 0}));
        Date d = new Date(ts.getTime());
        assertEquals(ts.toLocalDateTime(), invokeStatic("asLocalDateTime", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{d}, 0}));
        assertEquals(LocalDateTime.of(2020, 1, 2, 3, 4, 5), invokeStatic("asLocalDateTime", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{"2020-01-02T03:04:05"}, 0}));
        assertNull(invokeStatic("asLocalDateTime", new Class[]{Object[].class, int.class}, new Object[]{new Object[]{"bad"}, 0}));

        LocalDateTime s0 = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime e0 = s0.plusHours(5);
        assertEquals(TokenCostMetricsService.TimelineBucket.HOUR, invokeStatic("resolveBucket",
                new Class[]{LocalDateTime.class, LocalDateTime.class, TokenCostMetricsService.TimelineBucket.class},
                new Object[]{s0, e0, null}
        ));
        assertEquals(TokenCostMetricsService.TimelineBucket.DAY, invokeStatic("resolveBucket",
                new Class[]{LocalDateTime.class, LocalDateTime.class, TokenCostMetricsService.TimelineBucket.class},
                new Object[]{s0, s0.plusHours(100), null}
        ));
        assertEquals(TokenCostMetricsService.TimelineBucket.DAY, invokeStatic("resolveBucket",
                new Class[]{LocalDateTime.class, LocalDateTime.class, TokenCostMetricsService.TimelineBucket.class},
                new Object[]{s0, e0, TokenCostMetricsService.TimelineBucket.DAY}
        ));

        assertTrue(((String) invokeStatic("bucketExpr", new Class[]{String.class, TokenCostMetricsService.TimelineBucket.class},
                new Object[]{"t", TokenCostMetricsService.TimelineBucket.DAY})).contains("00:00:00"));
        assertTrue(((String) invokeStatic("bucketExpr", new Class[]{String.class, TokenCostMetricsService.TimelineBucket.class},
                new Object[]{"t", TokenCostMetricsService.TimelineBucket.HOUR})).contains("%H:00:00"));

        assertEquals(BigDecimal.ZERO, invokeStatic("computeCost",
                new Class[]{priceInfoClass(), LlmPricing.Mode.class, long.class, long.class},
                new Object[]{null, LlmPricing.Mode.DEFAULT, 1L, 1L}
        ));
        Object priceInfo = newPriceInfo("USD", LlmPricing.fromLegacy(BigDecimal.ONE, BigDecimal.ONE));
        BigDecimal cost = (BigDecimal) invokeStatic("computeCost",
                new Class[]{priceInfoClass(), LlmPricing.Mode.class, long.class, long.class},
                new Object[]{priceInfo, LlmPricing.Mode.DEFAULT, 1000L, 0L}
        );
        assertTrue(cost.compareTo(BigDecimal.ZERO) > 0);

        assertNull(invokeStatic("resolvePricing", new Class[]{LlmPriceConfigEntity.class}, new Object[]{null}));
        LlmPriceConfigEntity pcMeta = new LlmPriceConfigEntity();
        pcMeta.setName("x");
        pcMeta.setCurrency("cny");
        pcMeta.setMetadata(Map.of(
                "pricing", Map.of(
                        "defaultInputCostPerUnit", BigDecimal.ONE
                )
        ));
        assertNotNull(invokeStatic("resolvePricing", new Class[]{LlmPriceConfigEntity.class}, new Object[]{pcMeta}));

        LlmPriceConfigEntity pcLegacy = new LlmPriceConfigEntity();
        pcLegacy.setName("x");
        pcLegacy.setCurrency("cny");
        pcLegacy.setMetadata(Map.of());
        pcLegacy.setInputCostPer1k(BigDecimal.ONE);
        pcLegacy.setOutputCostPer1k(null);
        assertNotNull(invokeStatic("resolvePricing", new Class[]{LlmPriceConfigEntity.class}, new Object[]{pcLegacy}));
    }

    private static Object invokeStatic(String name, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m = TokenCostMetricsService.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object invokeRecordAccessor(Object rec, String accessor) throws Exception {
        Method m = rec.getClass().getDeclaredMethod(accessor);
        m.setAccessible(true);
        return m.invoke(rec);
    }

    private static Class<?> priceInfoClass() throws Exception {
        return Class.forName(TokenCostMetricsService.class.getName() + "$PriceInfo");
    }

    private static Object newPriceInfo(String currency, LlmPricing.Config pricing) throws Exception {
        Class<?> cls = priceInfoClass();
        Constructor<?> c = cls.getDeclaredConstructor(String.class, LlmPricing.Config.class);
        c.setAccessible(true);
        return c.newInstance(currency, pricing);
    }
}
