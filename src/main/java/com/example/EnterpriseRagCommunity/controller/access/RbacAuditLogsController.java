package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.RbacAuditQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.RbacAuditViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.RbacAuditLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.RbacAuditLogsRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/rbac-audit")
@RequiredArgsConstructor
public class RbacAuditLogsController {
    private final RbacAuditLogsRepository rbacAuditLogsRepository;

    @PostMapping("/query")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public ResponseEntity<Page<RbacAuditViewDTO>> query(@RequestBody RbacAuditQueryDTO q) {
        int page = q.getPageNum() != null && q.getPageNum() > 0 ? q.getPageNum() - 1 : 0;
        int size = q.getPageSize() != null && q.getPageSize() > 0 ? q.getPageSize() : 20;
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<RbacAuditLogsEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q.getActorUserId() != null) predicates.add(cb.equal(root.get("actorUserId"), q.getActorUserId()));
            if (StringUtils.hasText(q.getAction())) predicates.add(cb.equal(root.get("action"), q.getAction().trim()));
            if (StringUtils.hasText(q.getTargetType())) predicates.add(cb.equal(root.get("targetType"), q.getTargetType().trim()));
            if (StringUtils.hasText(q.getTargetId())) predicates.add(cb.like(root.get("targetId"), "%" + q.getTargetId().trim() + "%"));
            if (q.getCreatedAfter() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.getCreatedAfter()));
            if (q.getCreatedBefore() != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), q.getCreatedBefore()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RbacAuditLogsEntity> res = rbacAuditLogsRepository.findAll(spec, pageable);
        return ResponseEntity.ok(res.map(this::toView));
    }

    private RbacAuditViewDTO toView(RbacAuditLogsEntity e) {
        RbacAuditViewDTO dto = new RbacAuditViewDTO();
        dto.setId(e.getId());
        dto.setActorUserId(e.getActorUserId());
        dto.setAction(e.getAction());
        dto.setTargetType(e.getTargetType());
        dto.setTargetId(e.getTargetId());
        dto.setReason(e.getReason());
        dto.setDiffJson(e.getDiffJson());
        dto.setRequestIp(e.getRequestIp());
        dto.setUserAgent(e.getUserAgent());
        dto.setRequestId(e.getRequestId());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}

