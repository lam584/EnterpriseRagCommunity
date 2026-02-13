package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.AuditLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AuditLogsService {

    private final AuditLogsRepository auditLogsRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogsViewDTO> query(
            Integer page,
            Integer pageSize,
            String keyword,
            Long actorId,
            String actorName,
            String action,
            String op,
            String entityType,
            Long entityId,
            AuditResult result,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            String traceId,
            String sort
    ) {
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safePageSize = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 200);

        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, parseSort(sort));

        final String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        final String actorNameKw = StringUtils.hasText(actorName) ? actorName.trim() : null;
        final String traceKw = StringUtils.hasText(traceId) ? traceId.trim() : null;
        final String actionKw = StringUtils.hasText(action) ? action.trim() : null;
        final String opKw = StringUtils.hasText(op) ? op.trim() : null;
        final String entityTypeKw = StringUtils.hasText(entityType) ? entityType.trim() : null;

        Specification<AuditLogsEntity> spec = (root, query, cb) -> {
            // 兼容静态分析/极端实现：query 可能被标记为 nullable
            if (query != null) {
                query.distinct(true);
            }

            List<Predicate> ps = new ArrayList<>();

            if (actorId != null) {
                ps.add(cb.equal(root.get("actorUserId"), actorId));
            }
            if (actionKw != null) {
                ps.add(cb.like(root.get("action"), "%" + actionKw + "%"));
            }
            if (opKw != null) {
                ps.add(buildOpPredicate(opKw, root, cb));
            }
            if (entityTypeKw != null) {
                ps.add(cb.like(root.get("entityType"), "%" + entityTypeKw + "%"));
            }
            if (entityId != null) {
                ps.add(cb.equal(root.get("entityId"), entityId));
            }
            if (result != null) {
                ps.add(cb.equal(root.get("result"), result));
            }

            if (createdFrom != null || createdTo != null) {
                LocalDateTime start = createdFrom == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : createdFrom;
                LocalDateTime end = createdTo == null ? LocalDateTime.now().plusYears(100) : createdTo;
                ps.add(cb.between(root.get("createdAt"), start, end));
            }

            // Keyword search over core columns
            if (kw != null) {
                String like = "%" + kw + "%";
                ps.add(cb.or(
                        cb.like(root.get("action"), like),
                        cb.like(root.get("entityType"), like),
                        cb.like(root.get("details").as(String.class), like)
                ));
            }

            // Fallback keyword search over details (JSON stored via converter -> serialized by JPA provider)
            // This is best-effort: works if provider can cast to String.
            if (actorNameKw != null) {
                ps.add(cb.like(root.get("details").as(String.class), "%" + actorNameKw + "%"));
            }
            if (traceKw != null) {
                ps.add(cb.like(root.get("details").as(String.class), "%" + traceKw + "%"));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };

        return auditLogsRepository.findAll(spec, pageable).map(this::toViewDto);
    }

    private static Predicate buildOpPredicate(String opRaw, jakarta.persistence.criteria.Root<AuditLogsEntity> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        String op = opRaw == null ? "" : opRaw.trim().toUpperCase(Locale.ROOT);
        if (op.isEmpty()) {
            return cb.conjunction();
        }

        String field = "action";
        return switch (op) {
            case "CREATE" -> cb.or(
                    cb.equal(root.get(field), "CRUD_CREATE"),
                    cb.like(root.get(field), "%_CREATE"),
                    cb.like(root.get(field), "%_CREATE_%")
            );
            case "UPDATE" -> cb.or(
                    cb.equal(root.get(field), "CRUD_UPDATE"),
                    cb.like(root.get(field), "%_UPDATE"),
                    cb.like(root.get(field), "%_UPDATE_%"),
                    cb.like(root.get(field), "%_EDIT"),
                    cb.like(root.get(field), "%_EDIT_%")
            );
            case "DELETE" -> cb.or(
                    cb.equal(root.get(field), "CRUD_DELETE"),
                    cb.like(root.get(field), "%_DELETE"),
                    cb.like(root.get(field), "%_DELETE_%")
            );
            default -> cb.conjunction();
        };
    }

    @Transactional(readOnly = true)
    public AuditLogsViewDTO getById(Long id) {
        AuditLogsEntity e = auditLogsRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Audit log not found: " + id));
        return toViewDto(e);
    }

    private AuditLogsViewDTO toViewDto(AuditLogsEntity e) {
        Map<String, Object> details = e.getDetails();
        return new AuditLogsViewDTO(
                e.getId(),
                e.getCreatedAt(),
                e.getTenantId(),
                e.getActorUserId(),
                getString(details, "actorName"),
                e.getAction(),
                e.getEntityType(),
                e.getEntityId(),
                e.getResult(),
                getString(details, "message"),
                getString(details, "ip"),
                getString(details, "traceId"),
                getString(details, "method"),
                getString(details, "path"),
                getBoolean(details, "autoCrud"),
                details
        );
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean getBoolean(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return null;
    }

    private static Sort parseSort(String sort) {
        // Accept "createdAt,desc" (frontend default)
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }

        String[] parts = sort.split(",");
        String field = parts.length > 0 ? parts[0].trim() : "createdAt";
        String dir = parts.length > 1 ? parts[1].trim() : "desc";

        if (!StringUtils.hasText(field)) field = "createdAt";

        Sort.Direction d;
        try {
            d = Sort.Direction.fromString(dir);
        } catch (Exception ex) {
            d = Sort.Direction.DESC;
        }

        return Sort.by(new Sort.Order(d, field), new Sort.Order(Sort.Direction.DESC, "id"));
    }
}
