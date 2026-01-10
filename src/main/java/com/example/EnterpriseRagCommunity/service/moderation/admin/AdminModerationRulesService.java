package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesCreateDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRulesRepository;
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
import java.util.List;
import java.util.Map;

@Service
public class AdminModerationRulesService {

    private final ModerationRulesRepository repository;

    public AdminModerationRulesService(ModerationRulesRepository repository) {
        this.repository = repository;
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
        return repository.save(e);
    }

    @Transactional
    public ModerationRulesEntity update(Long id, ModerationRulesUpdateDTO dto) {
        ModerationRulesEntity e = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + id));

        if (dto.getName() != null && dto.getName().isPresent()) e.setName(dto.getName().get());
        if (dto.getType() != null && dto.getType().isPresent()) e.setType(dto.getType().get());
        if (dto.getPattern() != null && dto.getPattern().isPresent()) e.setPattern(dto.getPattern().get());
        if (dto.getSeverity() != null && dto.getSeverity().isPresent()) e.setSeverity(dto.getSeverity().get());
        if (dto.getEnabled() != null && dto.getEnabled().isPresent()) e.setEnabled(dto.getEnabled().get());
        if (dto.getMetadata() != null && dto.getMetadata().isPresent()) e.setMetadata(dto.getMetadata().get());

        return repository.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("规则不存在: " + id);
        }
        repository.deleteById(id);
    }
}
