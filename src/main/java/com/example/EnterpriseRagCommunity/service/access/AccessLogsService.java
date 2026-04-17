package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.AccessLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.ElasticsearchHttpSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AccessLogsService {

    private final AccessLogsRepository accessLogsRepository;

    @Autowired(required = false)
    private SystemConfigurationService systemConfigurationService;

    @Value("${app.logging.access.sink-mode:MYSQL}")
    private String sinkModeRaw = "MYSQL";

    private static final int MAX_LOG_PAGE_SIZE = 20_000;
    private static final String DEFAULT_ES_ACCESS_INDEX = "access-logs-v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Transactional(readOnly = true)
    public Page<AccessLogsViewDTO> query(
            Integer page,
            Integer pageSize,
            String keyword,
            Long userId,
            String username,
            String method,
            String path,
            Integer statusCode,
            String clientIp,
            String requestId,
            String traceId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            String sort
    ) {
        AccessLogSinkMode mode = resolveSinkMode();
        if (mode == AccessLogSinkMode.KAFKA) {
            return queryEs(
                    page, pageSize,
                    keyword,
                    userId,
                    username,
                    method,
                    path,
                    statusCode,
                    clientIp,
                    requestId,
                    traceId,
                    createdFrom,
                    createdTo,
                    sort
            );
        }

        return queryMysql(
                page, pageSize,
                keyword,
                userId,
                username,
                method,
                path,
                statusCode,
                clientIp,
                requestId,
                traceId,
                createdFrom,
                createdTo,
                sort
        );
    }

    private Page<AccessLogsViewDTO> queryMysql(
            Integer page,
            Integer pageSize,
            String keyword,
            Long userId,
            String username,
            String method,
            String path,
            Integer statusCode,
            String clientIp,
            String requestId,
            String traceId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            String sort
    ) {
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safePageSize = pageSize == null ? 20 : Math.clamp(pageSize, 1, MAX_LOG_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, parseSort(sort));

        final String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        final String usernameKw = StringUtils.hasText(username) ? username.trim() : null;
        final String methodKw = StringUtils.hasText(method) ? method.trim() : null;
        final String pathKw = StringUtils.hasText(path) ? path.trim() : null;
        final String ipKw = StringUtils.hasText(clientIp) ? clientIp.trim() : null;
        final String requestKw = StringUtils.hasText(requestId) ? requestId.trim() : null;
        final String traceKw = StringUtils.hasText(traceId) ? traceId.trim() : null;

        Specification<AccessLogsEntity> spec = (root, q, cb) -> {
            if (q != null) q.distinct(true);

            var ps = new ArrayList<Predicate>();

            // Only show non-archived logs by default
            ps.add(cb.isNull(root.get("archivedAt")));

            if (userId != null) ps.add(cb.equal(root.get("userId"), userId));
            if (statusCode != null) ps.add(cb.equal(root.get("statusCode"), statusCode));

            if (usernameKw != null) ps.add(cb.like(root.get("username"), "%" + usernameKw + "%"));
            if (methodKw != null) ps.add(cb.like(root.get("method"), "%" + methodKw + "%"));
            if (pathKw != null) ps.add(cb.like(root.get("path"), "%" + pathKw + "%"));
            if (ipKw != null) ps.add(cb.like(root.get("clientIp"), "%" + ipKw + "%"));
            if (requestKw != null) ps.add(cb.like(root.get("requestId"), "%" + requestKw + "%"));
            if (traceKw != null) ps.add(cb.like(root.get("traceId"), "%" + traceKw + "%"));

            LogTimeRangeSupport.addCreatedAtBetween(ps, root, cb, createdFrom, createdTo);

            if (kw != null) {
                String like = "%" + kw + "%";
                ps.add(cb.or(
                        cb.like(root.get("method"), like),
                        cb.like(root.get("path"), like),
                        cb.like(root.get("username"), like),
                        cb.like(root.get("clientIp"), like),
                        cb.like(root.get("requestId"), like),
                        cb.like(root.get("traceId"), like)
                ));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };

        return accessLogsRepository.findAll(spec, pageable).map(entity -> toViewDto(entity, false));
    }

    private Page<AccessLogsViewDTO> queryEs(
            Integer page,
            Integer pageSize,
            String keyword,
            Long userId,
            String username,
            String method,
            String path,
            Integer statusCode,
            String clientIp,
            String requestId,
            String traceId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            String sort
    ) {
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safePageSize = pageSize == null ? 20 : Math.clamp(pageSize, 1, MAX_LOG_PAGE_SIZE);
        int from = (safePage - 1) * safePageSize;

        String endpoint = resolveEsEndpoint();
        String indexName = resolveEsIndexName();
        String order = parseEsSortOrder(sort);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("from", from);
        root.put("size", safePageSize);
        root.put("track_total_hits", true);
        root.put("sort", List.of(
                Map.of("created_at", Map.of("order", order)),
            Map.of("event_id.keyword", Map.of(
                "order", order,
                "unmapped_type", "keyword"
            ))
        ));

        Map<String, Object> bool = new LinkedHashMap<>();
        List<Map<String, Object>> filter = new ArrayList<>();
        List<Map<String, Object>> must = new ArrayList<>();
        List<Map<String, Object>> should = new ArrayList<>();

        if (userId != null) {
            filter.add(Map.of("term", Map.of("user_id", userId)));
        }
        if (statusCode != null) {
            filter.add(Map.of("term", Map.of("status_code", statusCode)));
        }

        addWildcardMust(must, "username", username);
        addWildcardMust(must, "method", method);
        addWildcardMust(must, "path", path);
        addWildcardMust(must, "client_ip", clientIp);
        addWildcardMust(must, "request_id", requestId);
        addWildcardMust(must, "trace_id", traceId);

        if (createdFrom != null || createdTo != null) {
            Map<String, Object> rangeBody = new LinkedHashMap<>();
            if (createdFrom != null) rangeBody.put("gte", createdFrom.toString());
            if (createdTo != null) rangeBody.put("lte", createdTo.toString());
            filter.add(Map.of("range", Map.of("created_at", rangeBody)));
        }

        if (StringUtils.hasText(keyword)) {
            addWildcardShould(should, "method", keyword);
            addWildcardShould(should, "path", keyword);
            addWildcardShould(should, "username", keyword);
            addWildcardShould(should, "client_ip", keyword);
            addWildcardShould(should, "request_id", keyword);
            addWildcardShould(should, "trace_id", keyword);
        }

        if (!filter.isEmpty()) bool.put("filter", filter);
        if (!must.isEmpty()) bool.put("must", must);
        if (!should.isEmpty()) {
            bool.put("should", should);
            bool.put("minimum_should_match", 1);
        }
        root.put("query", Map.of("bool", bool));

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(root);
            JsonNode json = postEsSearch(endpoint, indexName, payload);
            JsonNode hitArray = json.path("hits").path("hits");
            List<AccessLogsViewDTO> items = new ArrayList<>();
            if (hitArray.isArray()) {
                for (int i = 0; i < hitArray.size(); i++) {
                    JsonNode source = hitArray.get(i).path("_source");
                    items.add(toViewDtoFromEs(source, from + i + 1L, false));
                }
            }

            long total = extractTotal(json.path("hits").path("total"), items.size());
            Pageable pageable = PageRequest.of(safePage - 1, safePageSize, parseSort(sort));
            return new PageImpl<>(items, pageable, total);
        } catch (Exception ex) {
            throw new IllegalStateException("Access logs ES query failed: " + ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public AccessLogsViewDTO getById(String idOrLookupId) {
        String lookupKey = normalizeLookupKey(idOrLookupId);
        if (lookupKey == null) {
            throw new NoSuchElementException("Access log not found: " + idOrLookupId);
        }

        AccessLogSinkMode mode = resolveSinkMode();
        if (mode == AccessLogSinkMode.KAFKA) {
            return getByLookupKeyFromEs(lookupKey);
        }
        return getByLookupKeyFromMysql(lookupKey);
    }

    private AccessLogsViewDTO toViewDto(AccessLogsEntity e, boolean includeDetails) {
        Map<String, Object> details = includeDetails ? e.getDetails() : null;
        return new AccessLogsViewDTO(
                e.getId(),
                e.getCreatedAt(),
                e.getTenantId(),
                e.getUserId(),
                e.getUsername(),
                e.getMethod(),
                e.getPath(),
                e.getQueryString(),
                e.getStatusCode(),
                e.getLatencyMs(),
                e.getClientIp(),
                e.getClientPort(),
                e.getServerIp(),
                e.getServerPort(),
                e.getScheme(),
                e.getHost(),
                e.getRequestId(),
                e.getTraceId(),
                e.getUserAgent(),
                e.getReferer(),
                details
        );
    }

    private static Sort parseSort(String sort) {
        return SortParsingSupport.parseCreatedAtIdSort(sort);
    }

    private AccessLogsViewDTO getByLookupKeyFromMysql(String lookupKey) {
        AccessLogsEntity entity = findMysqlEntityByLookupKey(lookupKey)
                .orElseThrow(() -> new NoSuchElementException("Access log not found: " + lookupKey));
        return toViewDto(entity, true);
    }

    private Optional<AccessLogsEntity> findMysqlEntityByLookupKey(String lookupKey) {
        Long numericId = parseLongStrict(lookupKey);
        if (numericId != null) {
            Optional<AccessLogsEntity> byId = accessLogsRepository.findById(numericId);
            if (byId.isPresent()) {
                return byId;
            }
        }

        Optional<AccessLogsEntity> byRequestId = accessLogsRepository.findFirstByRequestIdOrderByIdDesc(lookupKey);
        if (byRequestId.isPresent()) {
            return byRequestId;
        }
        return accessLogsRepository.findFirstByTraceIdOrderByIdDesc(lookupKey);
    }

    private AccessLogsViewDTO getByLookupKeyFromEs(String lookupKey) {
        String endpoint = resolveEsEndpoint();
        String indexName = resolveEsIndexName();

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("should", List.of(
                Map.of("term", Map.of("event_id", lookupKey)),
                Map.of("term", Map.of("request_id", lookupKey)),
                Map.of("term", Map.of("trace_id", lookupKey)),
                Map.of("term", Map.of("id", lookupKey))
        ));
        bool.put("minimum_should_match", 1);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("size", 1);
        root.put("query", Map.of("bool", bool));
        root.put("sort", List.of(Map.of("created_at", Map.of("order", "desc"))));

        try {
            JsonNode json = postEsSearch(endpoint, indexName, OBJECT_MAPPER.writeValueAsString(root));
            JsonNode hitArray = json.path("hits").path("hits");
            if (!hitArray.isArray() || hitArray.isEmpty()) {
                throw new NoSuchElementException("Access log not found: " + lookupKey);
            }

            JsonNode source = hitArray.get(0).path("_source");
            Long fallbackId = parseLongStrict(lookupKey);
            return toViewDtoFromEs(source, fallbackId, true);
        } catch (NoSuchElementException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Access logs ES detail query failed: " + ex.getMessage(), ex);
        }
    }

    private AccessLogSinkMode resolveSinkMode() {
        String raw = null;
        if (systemConfigurationService != null) {
            raw = systemConfigurationService.getConfig("app.logging.access.sink-mode");
        }
        if (!StringUtils.hasText(raw)) {
            raw = sinkModeRaw;
        }
        if (!StringUtils.hasText(raw)) return AccessLogSinkMode.MYSQL;
        try {
            return AccessLogSinkMode.valueOf(raw.trim().toUpperCase());
        } catch (Exception ignored) {
            return AccessLogSinkMode.MYSQL;
        }
    }

    private String resolveEsEndpoint() {
        if (systemConfigurationService == null) {
            return "http://127.0.0.1:9200";
        }
        return ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);
    }

    private String resolveEsIndexName() {
        if (systemConfigurationService == null) return DEFAULT_ES_ACCESS_INDEX;
        String idx = systemConfigurationService.getConfig("app.logging.access.es-sink.index");
        if (!StringUtils.hasText(idx)) return DEFAULT_ES_ACCESS_INDEX;
        return idx.trim();
    }

    private static String parseEsSortOrder(String sort) {
        if (!StringUtils.hasText(sort)) return "desc";
        String[] parts = sort.split(",");
        if (parts.length < 2) return "desc";
        String dir = parts[1] == null ? "" : parts[1].trim().toLowerCase();
        return "asc".equals(dir) ? "asc" : "desc";
    }

    private static void addWildcardMust(List<Map<String, Object>> must, String field, String value) {
        if (!StringUtils.hasText(value)) return;
        must.add(Map.of(
                "wildcard",
                Map.of(field, Map.of("value", toWildcardPattern(value), "case_insensitive", true))
        ));
    }

    private static void addWildcardShould(List<Map<String, Object>> should, String field, String value) {
        if (!StringUtils.hasText(value)) return;
        should.add(Map.of(
                "wildcard",
                Map.of(field, Map.of("value", toWildcardPattern(value), "case_insensitive", true))
        ));
    }

    private static String toWildcardPattern(String value) {
        String trimmed = value == null ? "" : value.trim();
        String escaped = trimmed.replace("*", "\\\\*").replace("?", "\\\\?");
        return "*" + escaped + "*";
    }

    private static AccessLogsViewDTO toViewDtoFromEs(JsonNode source, Long fallbackId, boolean includeDetails) {
        Long id = longVal(source, "id");
        if (id == null) id = fallbackId;
        LocalDateTime createdAt = parseEsDateTime(firstNonBlank(
                text(source, "created_at"),
                text(source, "@timestamp"),
                text(source, "event_time")
        ));
        Map<String, Object> details = includeDetails ? mapNode(source.get("details")) : null;

        return new AccessLogsViewDTO(
                id,
                createdAt,
                longVal(source, "tenant_id"),
                longVal(source, "user_id"),
                text(source, "username"),
                text(source, "method"),
                text(source, "path"),
                text(source, "query_string"),
                intVal(source, "status_code"),
                intVal(source, "latency_ms"),
                text(source, "client_ip"),
                intVal(source, "client_port"),
                text(source, "server_ip"),
                intVal(source, "server_port"),
                text(source, "scheme"),
                text(source, "host"),
                text(source, "request_id"),
                text(source, "trace_id"),
                text(source, "user_agent"),
                text(source, "referer"),
                details
        );
    }

    private JsonNode postEsSearch(String endpoint, String indexName, String payload) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(endpoint + "/" + indexName + "/_search").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (systemConfigurationService != null) {
            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Access logs ES query failed HTTP " + code + ": " + body);
        }
        return OBJECT_MAPPER.readTree(body);
    }

    private static long extractTotal(JsonNode totalNode, int fallbackSize) {
        if (totalNode == null || totalNode.isMissingNode() || totalNode.isNull()) return fallbackSize;
        if (totalNode.isNumber()) return totalNode.asLong();
        JsonNode value = totalNode.get("value");
        if (value != null && value.isNumber()) return value.asLong();
        return fallbackSize;
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        String out = field.asText(null);
        if (!StringUtils.hasText(out)) return null;
        return out.trim();
    }

    private static Integer intVal(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (field.isNumber()) return field.asInt();
        String s = field.asText(null);
        if (!StringUtils.hasText(s)) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long longVal(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (field.isNumber()) return field.asLong();
        String s = field.asText(null);
        if (!StringUtils.hasText(s)) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return OBJECT_MAPPER.convertValue(node, Map.class);
    }

    private static String normalizeLookupKey(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        return raw.trim();
    }

    private static Long parseLongStrict(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDateTime parseEsDateTime(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String v = raw.trim();
        try {
            return LocalDateTime.parse(v);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(v).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }
}
