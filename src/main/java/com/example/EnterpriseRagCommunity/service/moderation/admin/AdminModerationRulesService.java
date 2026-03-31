package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesCreateDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRulesRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminModerationRulesService {

    private final ModerationRulesRepository repository;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    public AdminModerationRulesService(ModerationRulesRepository repository, AuditLogWriter auditLogWriter, AuditDiffBuilder auditDiffBuilder) {
        this.repository = repository;
        this.auditLogWriter = auditLogWriter;
        this.auditDiffBuilder = auditDiffBuilder;
    }

    public Page<ModerationRulesEntity> list(int page, int pageSize,
                                           String q,
                                           com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType type,
                                           com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity severity,
                                           Boolean enabled,
                                           String category) {

        int p0 = Math.max(0, page - 1);
        int size = Math.min(Math.max(1, pageSize), 200);
        Pageable pageable = PageRequest.of(p0, size, Sort.by(Sort.Direction.DESC, "id"));

        Specification<ModerationRulesEntity> spec = (root, _query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.trim().isEmpty()) {
                String like = "%" + q.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("pattern"), like)
                ));
            }
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (severity != null) predicates.add(cb.equal(root.get("severity"), severity));
            if (enabled != null) predicates.add(cb.equal(root.get("enabled"), enabled));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ModerationRulesEntity> pageRes = repository.findAll(spec, pageable);
        if (category == null || category.isBlank()) return pageRes;

        List<ModerationRulesEntity> filtered = new ArrayList<>();
        for (ModerationRulesEntity e : pageRes.getContent()) {
            Map<String, Object> md = e.getMetadata();
            if (md == null) continue;
            Object c = md.get("category");
            if (category.equals(String.valueOf(c))) filtered.add(e);
        }

        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Transactional
    public ModerationRulesEntity create(ModerationRulesCreateDTO dto) {
        ModerationRulesEntity e = new ModerationRulesEntity();
        e.setName(dto.getName());
        e.setType(dto.getType());
        e.setPattern(dto.getPattern());
        e.setSeverity(dto.getSeverity());
        e.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        e.setMetadata(dto.getMetadata());
        e.setCreatedAt(LocalDateTime.now());
        ModerationRulesEntity saved = repository.save(e);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_RULE_CREATE",
                "MODERATION_RULE",
                saved.getId(),
                AuditResult.SUCCESS,
                "创建审核规则",
                null,
                auditDiffBuilder.build(Map.of(), summarize(saved))
        );
        return saved;
    }

    @Transactional
    public ModerationRulesEntity update(Long id, ModerationRulesUpdateDTO dto) {
        ModerationRulesEntity e = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + id));
        Map<String, Object> before = summarize(e);

        if (dto.isHasName()) e.setName(dto.getName());
        if (dto.isHasType()) e.setType(dto.getType());
        if (dto.isHasPattern()) e.setPattern(dto.getPattern());
        if (dto.isHasSeverity()) e.setSeverity(dto.getSeverity());
        if (dto.isHasEnabled()) e.setEnabled(dto.getEnabled());
        if (dto.isHasMetadata()) e.setMetadata(dto.getMetadata());

        ModerationRulesEntity saved = repository.save(e);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_RULE_UPDATE",
                "MODERATION_RULE",
                saved.getId(),
                AuditResult.SUCCESS,
                "更新审核规则",
                null,
                auditDiffBuilder.build(before, summarize(saved))
        );
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ModerationRulesEntity e = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + id));
        Map<String, Object> before = summarize(e);
        repository.deleteById(id);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_RULE_DELETE",
                "MODERATION_RULE",
                id,
                AuditResult.SUCCESS,
                "删除审核规则",
                null,
                auditDiffBuilder.build(before, Map.of())
        );
    }

    private static Map<String, Object> summarize(ModerationRulesEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (e == null) return m;
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("type", e.getType());
        m.put("pattern", e.getPattern());
        m.put("severity", e.getSeverity());
        m.put("enabled", e.getEnabled());
        m.put("metadata", e.getMetadata());
        return m;
    }

    private static String currentUsernameOrNull() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
            String name = auth.getName();
            return name == null || name.isBlank() ? null : name.trim();
        } catch (Exception e) {
            return null;
        }
    }
}
