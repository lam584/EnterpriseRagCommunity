package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenMetricsModelItemDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenMetricsResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenTimelinePointDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenTimelineResponseDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TokenCostMetricsService {

    private static final String ENV_DEFAULT = "default";

    @PersistenceContext
    private EntityManager entityManager;

    private final LlmModelRepository llmModelRepository;
    private final LlmPriceConfigRepository llmPriceConfigRepository;

    @Transactional(readOnly = true)
    public AdminTokenMetricsResponseDTO query(LocalDateTime start, LocalDateTime end) {
        return query(start, end, TokenMetricsSource.ALL, LlmPricing.Mode.DEFAULT);
    }

    @Transactional(readOnly = true)
    public AdminTokenMetricsResponseDTO query(LocalDateTime start, LocalDateTime end, TokenMetricsSource source) {
        return query(start, end, source, LlmPricing.Mode.DEFAULT);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static NormalizedRange normalizeRange(LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime s = start == null ? now.minusDays(7) : start;
        LocalDateTime e = end == null ? now : end;
        if (s.isAfter(e)) {
            LocalDateTime t = s;
            s = e;
            e = t;
        }
        return new NormalizedRange(s, e);
    }

    @Transactional(readOnly = true)
    public AdminTokenMetricsResponseDTO query(LocalDateTime start, LocalDateTime end, String source, LlmPricing.Mode pricingMode) {
        NormalizedRange range = normalizeRange(start, end);
        LocalDateTime s = range.start();
        LocalDateTime e = range.end();

        SourceFilter src = parseSource(source);
        LlmPricing.Mode pm = pricingMode == null ? LlmPricing.Mode.DEFAULT : pricingMode;
        List<UsageRow> rows = new ArrayList<>();
        if (src.kind == SourceKind.ALL_SCENARIOS) {
            rows.addAll(loadScenarioRows(s, e, null));
        } else if (src.kind == SourceKind.TASK_TYPE) {
            rows.addAll(loadScenarioRows(s, e, src.taskType));
        } else if (src.kind == SourceKind.LEGACY_CHAT) {
            rows.addAll(loadLegacyChatRows(s, e));
        } else if (src.kind == SourceKind.LEGACY_MODERATION) {
            rows.addAll(loadModerationRows(s, e));
        } else if (src.kind == SourceKind.LEGACY_JOB) {
            rows.addAll(loadJobRows(s, e));
        }

        Set<String> models = new HashSet<>();
        for (UsageRow r : rows) {
            String m = normalizeModel(r.model());
            models.add(m);
        }

        Map<String, PriceInfo> priceByModel = resolvePrices(models);

        Map<String, Agg> aggByModel = new HashMap<>();
        Set<String> currencies = new HashSet<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalTokens = 0;

        for (UsageRow r : rows) {
            String model = normalizeModel(r.model());
            long in = r.tokensIn() == null ? 0L : Math.max(0, r.tokensIn());
            long out = r.tokensOut() == null ? 0L : Math.max(0, r.tokensOut());

            PriceInfo price = priceByModel.get(model);
            BigDecimal cost = computeCost(price, pm, in, out);
            if (price != null && price.currency() != null && !price.currency().isBlank() && cost.signum() != 0) {
                currencies.add(price.currency().trim().toUpperCase());
            }

            Agg a = aggByModel.computeIfAbsent(model, k -> new Agg());
            a.tokensIn += in;
            a.tokensOut += out;
            a.totalTokens += (in + out);
            a.cost = a.cost.add(cost);
        }

        for (Agg a : aggByModel.values()) {
            totalTokens += a.totalTokens;
            totalCost = totalCost.add(a.cost);
        }

        String currency = null;
        if (currencies.size() == 1) currency = currencies.iterator().next();
        if (currencies.size() > 1) currency = "MIXED";

        List<AdminTokenMetricsModelItemDTO> items = new ArrayList<>();
        for (Map.Entry<String, Agg> en : aggByModel.entrySet()) {
            String model = en.getKey();
            Agg a = en.getValue();

            AdminTokenMetricsModelItemDTO it = new AdminTokenMetricsModelItemDTO();
            it.setModel(model);
            it.setTokensIn(a.tokensIn);
            it.setTokensOut(a.tokensOut);
            it.setTotalTokens(a.totalTokens);
            it.setCost(a.cost);

            PriceInfo price = priceByModel.get(model);
            it.setPriceMissing(price == null || !LlmPricing.isConfiguredForMode(price.pricing(), pm));
            items.add(it);
        }

        items.sort(Comparator
                .comparing(AdminTokenMetricsModelItemDTO::getCost).reversed()
                .thenComparing(AdminTokenMetricsModelItemDTO::getTotalTokens, Comparator.reverseOrder())
                .thenComparing(AdminTokenMetricsModelItemDTO::getModel));

        AdminTokenMetricsResponseDTO resp = new AdminTokenMetricsResponseDTO();
        resp.setStart(s);
        resp.setEnd(e);
        resp.setCurrency(currency);
        resp.setTotalTokens(totalTokens);
        resp.setTotalCost(totalCost);
        resp.setItems(items);
        return resp;
    }

    @Transactional(readOnly = true)
    public AdminTokenMetricsResponseDTO query(LocalDateTime start, LocalDateTime end, TokenMetricsSource source, LlmPricing.Mode pricingMode) {
        String s = enumName(source);
        return query(start, end, s, pricingMode);
    }

    @Transactional(readOnly = true)
    public AdminTokenTimelineResponseDTO queryTimeline(LocalDateTime start, LocalDateTime end, String source, TimelineBucket bucket) {
        NormalizedRange range = normalizeRange(start, end);
        LocalDateTime s = range.start();
        LocalDateTime e = range.end();

        SourceFilter src = parseSource(source);
        TimelineBucket buck = resolveBucket(s, e, bucket);

        List<TimelineRow> rows = new ArrayList<>();
        if (src.kind == SourceKind.ALL_SCENARIOS) {
            rows.addAll(loadScenarioTimelineRows(s, e, buck, null));
        } else if (src.kind == SourceKind.TASK_TYPE) {
            rows.addAll(loadScenarioTimelineRows(s, e, buck, src.taskType));
        } else if (src.kind == SourceKind.LEGACY_CHAT) {
            rows.addAll(loadLegacyChatTimelineRows(s, e, buck));
        } else if (src.kind == SourceKind.LEGACY_MODERATION) {
            rows.addAll(loadModerationTimelineRows(s, e, buck));
        } else if (src.kind == SourceKind.LEGACY_JOB) {
            rows.addAll(loadJobTimelineRows(s, e, buck));
        }

        LocalDateTime startBucket = truncateToBucket(s, buck);
        LocalDateTime endBucket = truncateToBucket(e, buck);

        Map<LocalDateTime, TimelineAgg> byTime = new HashMap<>();
        for (TimelineRow r : rows) {
            if (r.time() == null) continue;
            LocalDateTime t = truncateToBucket(r.time(), buck);
            TimelineAgg a = byTime.computeIfAbsent(t, k -> new TimelineAgg());
            long in = r.tokensIn() == null ? 0L : Math.max(0, r.tokensIn());
            long out = r.tokensOut() == null ? 0L : Math.max(0, r.tokensOut());
            a.tokensIn += in;
            a.tokensOut += out;
        }

        List<AdminTokenTimelinePointDTO> points = new ArrayList<>();
        long totalTokens = 0;
        for (LocalDateTime t = startBucket; !t.isAfter(endBucket); t = plusBucket(t, buck)) {
            TimelineAgg a = byTime.get(t);
            long in = a == null ? 0L : a.tokensIn;
            long out = a == null ? 0L : a.tokensOut;
            long tt = in + out;
            totalTokens += tt;

            AdminTokenTimelinePointDTO p = new AdminTokenTimelinePointDTO();
            p.setTime(t);
            p.setTokensIn(in);
            p.setTokensOut(out);
            p.setTotalTokens(tt);
            points.add(p);
        }

        AdminTokenTimelineResponseDTO resp = new AdminTokenTimelineResponseDTO();
        resp.setStart(s);
        resp.setEnd(e);
        resp.setSource(src.source);
        resp.setBucket(buck.name());
        resp.setTotalTokens(totalTokens);
        resp.setPoints(points);
        return resp;
    }

    private List<UsageRow> loadScenarioRows(LocalDateTime start, LocalDateTime end, String taskType) {
        List<?> raw;
        if (taskType != null) {
            raw = entityManager.createNativeQuery("""
                            SELECT h.model,
                                   IFNULL(h.tokens_in, IFNULL(h.total_tokens, 0)) AS tokens_in,
                                   IFNULL(h.tokens_out, 0) AS tokens_out
                            FROM llm_queue_task_history h
                            WHERE h.finished_at BETWEEN ?1 AND ?2
                              AND h.status = 'DONE'
                              AND h.type = ?3
                            """)
                    .setParameter(1, start)
                    .setParameter(2, end)
                    .setParameter(3, taskType)
                    .getResultList();
        } else {
            raw = entityManager.createNativeQuery("""
                            SELECT h.model,
                                   IFNULL(h.tokens_in, IFNULL(h.total_tokens, 0)) AS tokens_in,
                                   IFNULL(h.tokens_out, 0) AS tokens_out
                            FROM llm_queue_task_history h
                            WHERE h.finished_at BETWEEN ?1 AND ?2
                              AND h.status = 'DONE'
                            """)
                    .setParameter(1, start)
                    .setParameter(2, end)
                    .getResultList();
        }

        List<UsageRow> out = new ArrayList<>();
        for (Object row : raw) {
            out.add(new UsageRow(
                    asString((Object[]) row, 0),
                    asLong((Object[]) row, 1),
                    asLong((Object[]) row, 2)
            ));
        }
        return out;
    }

    private List<TimelineRow> loadScenarioTimelineRows(LocalDateTime start, LocalDateTime end, TimelineBucket bucket, String taskType) {
        String tcol = bucketExpr("h.finished_at", bucket);
        List<?> raw;
        if (taskType != null) {
            raw = entityManager.createNativeQuery("""
                            SELECT %s AS t,
                                   SUM(IFNULL(h.tokens_in, IFNULL(h.total_tokens, 0))) AS tokens_in,
                                   SUM(IFNULL(h.tokens_out, 0)) AS tokens_out
                            FROM llm_queue_task_history h
                            WHERE h.finished_at BETWEEN ?1 AND ?2
                              AND h.status = 'DONE'
                              AND h.type = ?3
                            GROUP BY t
                            ORDER BY t
                            """.formatted(tcol))
                    .setParameter(1, start)
                    .setParameter(2, end)
                    .setParameter(3, taskType)
                    .getResultList();
        } else {
            raw = entityManager.createNativeQuery("""
                            SELECT %s AS t,
                                   SUM(IFNULL(h.tokens_in, IFNULL(h.total_tokens, 0))) AS tokens_in,
                                   SUM(IFNULL(h.tokens_out, 0)) AS tokens_out
                            FROM llm_queue_task_history h
                            WHERE h.finished_at BETWEEN ?1 AND ?2
                              AND h.status = 'DONE'
                            GROUP BY t
                            ORDER BY t
                            """.formatted(tcol))
                    .setParameter(1, start)
                    .setParameter(2, end)
                    .getResultList();
        }

        List<TimelineRow> out = new ArrayList<>();
        for (Object row : raw) {
            out.add(new TimelineRow(
                    asLocalDateTime((Object[]) row, 0),
                    asLong((Object[]) row, 1),
                    asLong((Object[]) row, 2)
            ));
        }
        return out;
    }

    private List<UsageRow> loadLegacyChatRows(LocalDateTime start, LocalDateTime end) {
        @SuppressWarnings("unchecked")
        List<Object[]> raw = entityManager.createNativeQuery("""
                        SELECT h.model,
                               IFNULL(h.tokens_in, IFNULL(h.total_tokens, 0)) AS tokens_in,
                               IFNULL(h.tokens_out, 0) AS tokens_out
                        FROM llm_queue_task_history h
                        WHERE h.finished_at BETWEEN ?1 AND ?2
                          AND h.status = 'DONE'
                                                    AND h.type IN ('MULTIMODAL_CHAT', 'TEXT_CHAT', 'IMAGE_CHAT')
                        """)
                .setParameter(1, start)
                .setParameter(2, end)
                .getResultList();

        List<UsageRow> out = new ArrayList<>();
        for (Object[] row : raw) {
            out.add(new UsageRow(
                    asString(row, 0),
                    asLong(row, 1),
                    asLong(row, 2)
            ));
        }
        return out;
    }

    private List<TimelineRow> loadLegacyChatTimelineRows(LocalDateTime start, LocalDateTime end, TimelineBucket bucket) {
        String tcol = bucketExpr("h.finished_at", bucket);
        @SuppressWarnings("unchecked")
        List<Object[]> raw = entityManager.createNativeQuery("""
                        SELECT %s AS t,
                               SUM(IFNULL(h.tokens_in, IFNULL(h.total_tokens, 0))) AS tokens_in,
                               SUM(IFNULL(h.tokens_out, 0)) AS tokens_out
                        FROM llm_queue_task_history h
                        WHERE h.finished_at BETWEEN ?1 AND ?2
                          AND h.status = 'DONE'
                                                    AND h.type IN ('MULTIMODAL_CHAT', 'TEXT_CHAT', 'IMAGE_CHAT')
                        GROUP BY t
                        ORDER BY t
                        """.formatted(tcol))
                .setParameter(1, start)
                .setParameter(2, end)
                .getResultList();

        List<TimelineRow> out = new ArrayList<>();
        for (Object[] row : raw) {
            out.add(new TimelineRow(
                    asLocalDateTime(row, 0),
                    asLong(row, 1),
                    asLong(row, 2)
            ));
        }
        return out;
    }

    private List<UsageRow> loadModerationRows(LocalDateTime start, LocalDateTime end) {
        @SuppressWarnings("unchecked")
        List<Object[]> raw = entityManager.createNativeQuery("""
                        SELECT h.model,
                               IFNULL(h.tokens_in, IFNULL(h.total_tokens, 0)) AS tokens_in,
                               IFNULL(h.tokens_out, 0) AS tokens_out
                        FROM llm_queue_task_history h
                        WHERE h.finished_at BETWEEN ?1 AND ?2
                          AND h.status = 'DONE'
                                                    AND h.type IN ('MULTIMODAL_MODERATION', 'TEXT_MODERATION', 'IMAGE_MODERATION', 'MODERATION_CHUNK', 'SIMILARITY_EMBEDDING')
                        """)
                .setParameter(1, start)
                .setParameter(2, end)
                .getResultList();

        List<UsageRow> out = new ArrayList<>();
        for (Object[] row : raw) {
            out.add(new UsageRow(
                    asString(row, 0),
                    asLong(row, 1),
                    asLong(row, 2)
            ));
        }
        return out;
    }

    private List<TimelineRow> loadModerationTimelineRows(LocalDateTime start, LocalDateTime end, TimelineBucket bucket) {
        String tcol = bucketExpr("h.finished_at", bucket);
        @SuppressWarnings("unchecked")
        List<Object[]> raw = entityManager.createNativeQuery("""
                        SELECT %s AS t,
                               SUM(IFNULL(h.tokens_in, IFNULL(h.total_tokens, 0))) AS tokens_in,
                               SUM(IFNULL(h.tokens_out, 0)) AS tokens_out
                        FROM llm_queue_task_history h
                        WHERE h.finished_at BETWEEN ?1 AND ?2
                          AND h.status = 'DONE'
                                                    AND h.type IN ('MULTIMODAL_MODERATION', 'TEXT_MODERATION', 'IMAGE_MODERATION', 'MODERATION_CHUNK', 'SIMILARITY_EMBEDDING')
                        GROUP BY t
                        ORDER BY t
                        """.formatted(tcol))
                .setParameter(1, start)
                .setParameter(2, end)
                .getResultList();

        List<TimelineRow> out = new ArrayList<>();
        for (Object[] row : raw) {
            out.add(new TimelineRow(
                    asLocalDateTime(row, 0),
                    asLong(row, 1),
                    asLong(row, 2)
            ));
        }
        return out;
    }

    private List<UsageRow> loadJobRows(LocalDateTime start, LocalDateTime end) {
        @SuppressWarnings("unchecked")
        List<Object[]> raw = entityManager.createNativeQuery("""
                        SELECT j.model, j.tokens_in, j.tokens_out
                        FROM generation_jobs j
                        WHERE j.created_at BETWEEN ?1 AND ?2
                        """)
                .setParameter(1, start)
                .setParameter(2, end)
                .getResultList();

        List<UsageRow> out = new ArrayList<>();
        for (Object[] row : raw) {
            out.add(new UsageRow(
                    asString(row, 0),
                    asLong(row, 1),
                    asLong(row, 2)
            ));
        }
        return out;
    }

    private List<TimelineRow> loadJobTimelineRows(LocalDateTime start, LocalDateTime end, TimelineBucket bucket) {
        String tcol = bucketExpr("j.created_at", bucket);
        @SuppressWarnings("unchecked")
        List<Object[]> raw = entityManager.createNativeQuery("""
                        SELECT %s AS t, SUM(IFNULL(j.tokens_in, 0)) AS tokens_in, SUM(IFNULL(j.tokens_out, 0)) AS tokens_out
                        FROM generation_jobs j
                        WHERE j.created_at BETWEEN ?1 AND ?2
                        GROUP BY t
                        ORDER BY t
                        """.formatted(tcol))
                .setParameter(1, start)
                .setParameter(2, end)
                .getResultList();

        List<TimelineRow> out = new ArrayList<>();
        for (Object[] row : raw) {
            out.add(new TimelineRow(
                    asLocalDateTime(row, 0),
                    asLong(row, 1),
                    asLong(row, 2)
            ));
        }
        return out;
    }

    private Map<String, PriceInfo> resolvePrices(Collection<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) return Map.of();

        Map<String, PriceInfo> out = new HashMap<>();
        List<LlmModelEntity> modelRows = llmModelRepository.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(
                ENV_DEFAULT,
                modelNames
        );

        Map<String, Long> modelToPriceId = new HashMap<>();
        Set<Long> priceIds = new HashSet<>();
        for (LlmModelEntity m : modelRows) {
            if (m == null) continue;
            String name = normalizeModel(m.getModelName());
            if (modelToPriceId.containsKey(name)) continue;
            Long pid = m.getPriceConfigId();
            if (pid == null) continue;
            modelToPriceId.put(name, pid);
            priceIds.add(pid);
        }

        if (!priceIds.isEmpty()) {
            List<LlmPriceConfigEntity> pcs = llmPriceConfigRepository.findByIdIn(priceIds);
            Map<Long, LlmPriceConfigEntity> byId = new HashMap<>();
            for (LlmPriceConfigEntity pc : pcs) {
                if (pc == null || pc.getId() == null) continue;
                byId.put(pc.getId(), pc);
            }
            for (Map.Entry<String, Long> en : modelToPriceId.entrySet()) {
                LlmPriceConfigEntity pc = byId.get(en.getValue());
                if (pc == null) continue;
                out.put(en.getKey(), new PriceInfo(
                        normalizeCurrency(pc.getCurrency()),
                        resolvePricing(pc)
                ));
            }
        }

        Set<String> missing = new HashSet<>();
        for (String m : modelNames) {
            String name = normalizeModel(m);
            if (!out.containsKey(name)) missing.add(name);
        }

        if (!missing.isEmpty()) {
            List<LlmPriceConfigEntity> pcs = llmPriceConfigRepository.findByNameIn(missing);
            for (LlmPriceConfigEntity pc : pcs) {
                if (pc == null) continue;
                String name = normalizeModel(pc.getName());
                out.putIfAbsent(name, new PriceInfo(
                        normalizeCurrency(pc.getCurrency()),
                        resolvePricing(pc)
                ));
            }
        }

        return out;
    }

    private static LlmPricing.Config resolvePricing(LlmPriceConfigEntity pc) {
        if (pc == null) return null;
        LlmPricing.Config meta = LlmPricing.fromMetadata(pc.getMetadata());
        if (meta != null) return meta;
        return LlmPricing.fromLegacy(pc.getInputCostPer1k(), pc.getOutputCostPer1k());
    }

    private static BigDecimal computeCost(PriceInfo price, LlmPricing.Mode pricingMode, long tokensIn, long tokensOut) {
        if (price == null) return BigDecimal.ZERO;
        return TokenCostCalculator.computeCost(price.pricing(), pricingMode, tokensIn, tokensOut);
    }

    private static String normalizeModel(String model) {
        if (model == null) return "UNKNOWN";
        String s = model.trim();
        return s.isBlank() ? "UNKNOWN" : s;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null) return null;
        String s = currency.trim();
        return s.isBlank() ? null : s.toUpperCase();
    }

    private static String asString(Object[] row, int idx) {
        if (row == null || idx >= row.length) return null;
        Object v = row[idx];
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private static Long asLong(Object[] row, int idx) {
        if (row == null || idx >= row.length) return null;
        Object v = row[idx];
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime asLocalDateTime(Object[] row, int idx) {
        if (row == null || idx >= row.length) return null;
        Object v = row[idx];
        switch (v) {
            case null -> {
                return null;
            }
            case Timestamp ts -> {
                return ts.toLocalDateTime();
            }
            case java.util.Date d -> {
                return new Timestamp(d.getTime()).toLocalDateTime();
            }
            default -> {
            }
        }
        try {
            return LocalDateTime.parse(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static TimelineBucket resolveBucket(LocalDateTime start, LocalDateTime end, TimelineBucket bucket) {
        TimelineBucket b = bucket == null ? TimelineBucket.AUTO : bucket;
        if (b != TimelineBucket.AUTO) return b;
        long hours = Math.abs(Duration.between(start, end).toHours());
        return hours > 72 ? TimelineBucket.DAY : TimelineBucket.HOUR;
    }

    private static LocalDateTime truncateToBucket(LocalDateTime t, TimelineBucket bucket) {
        if (t == null) return null;
        if (bucket == TimelineBucket.DAY) {
            return t.withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        return t.withMinute(0).withSecond(0).withNano(0);
    }

    private static LocalDateTime plusBucket(LocalDateTime t, TimelineBucket bucket) {
        if (bucket == TimelineBucket.DAY) return t.plusDays(1);
        return t.plusHours(1);
    }

    private static String bucketExpr(String col, TimelineBucket bucket) {
        if (bucket == TimelineBucket.DAY) {
            return "STR_TO_DATE(DATE_FORMAT(" + col + ", '%Y-%m-%d 00:00:00'), '%Y-%m-%d %H:%i:%s')";
        }
        return "STR_TO_DATE(DATE_FORMAT(" + col + ", '%Y-%m-%d %H:00:00'), '%Y-%m-%d %H:%i:%s')";
    }

    @Transactional(readOnly = true)
    public AdminTokenTimelineResponseDTO queryTimeline(LocalDateTime start, LocalDateTime end, TokenMetricsSource source, TimelineBucket bucket) {
        String s = enumName(source);
        return queryTimeline(start, end, s, bucket);
    }

    private record UsageRow(
            String model,
            Long tokensIn,
            Long tokensOut
    ) {
    }

    private record TimelineRow(
            LocalDateTime time,
            Long tokensIn,
            Long tokensOut
    ) {
    }

    private static class Agg {
        long tokensIn;
        long tokensOut;
        long totalTokens;
        BigDecimal cost = BigDecimal.ZERO;
    }

    private static class TimelineAgg {
        long tokensIn;
        long tokensOut;
    }

    private record PriceInfo(
            String currency,
            LlmPricing.Config pricing
    ) {
    }

    private record NormalizedRange(
            LocalDateTime start,
            LocalDateTime end
    ) {
    }

    private enum SourceKind {
        ALL_SCENARIOS,
        TASK_TYPE,
        LEGACY_CHAT,
        LEGACY_MODERATION,
        LEGACY_JOB
    }

    private record SourceFilter(
            SourceKind kind,
            String source,
            String taskType
    ) {
    }

    private static SourceFilter parseSource(String source) {
        String raw = source == null ? "" : source.trim();
        if (raw.isBlank() || raw.equalsIgnoreCase("ALL")) return new SourceFilter(SourceKind.ALL_SCENARIOS, "ALL", null);
        String up = raw.toUpperCase(Locale.ROOT);
        return switch (up) {
            case "CHAT" -> new SourceFilter(SourceKind.LEGACY_CHAT, "CHAT", null);
            case "MODERATION" -> new SourceFilter(SourceKind.LEGACY_MODERATION, "MODERATION", null);
            case "JOB" -> new SourceFilter(SourceKind.LEGACY_JOB, "JOB", null);
            default -> new SourceFilter(SourceKind.TASK_TYPE, up, up);
        };
    }

    public enum TimelineBucket {
        AUTO,
        HOUR,
        DAY;

        public static TimelineBucket fromNullableString(String v) {
            if (v == null) return null;
            String s = v.trim();
            if (s.isBlank()) return null;
            try {
                return TimelineBucket.valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }

    public enum TokenMetricsSource {
        ALL,
        CHAT,
        MODERATION,
        JOB;

        public static TokenMetricsSource fromNullableString(String v) {
            if (v == null) return null;
            String s = v.trim();
            if (s.isBlank()) return null;
            try {
                return TokenMetricsSource.valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
