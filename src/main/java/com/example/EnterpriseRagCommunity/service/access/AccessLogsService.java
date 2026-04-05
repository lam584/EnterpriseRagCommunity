package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.AccessLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AccessLogsService {

    private final AccessLogsRepository accessLogsRepository;

    private static final int MAX_LOG_PAGE_SIZE = 20_000;

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
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safePageSize = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), MAX_LOG_PAGE_SIZE);
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

    @Transactional(readOnly = true)
    public AccessLogsViewDTO getById(Long id) {
        AccessLogsEntity e = accessLogsRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Access log not found: " + id));
        return toViewDto(e, true);
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
}
