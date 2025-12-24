package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.service.content.TagsService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagsServiceImpl implements TagsService {

    private final TagsRepository tagsRepository;

    @Override
    public Page<TagsEntity> query(TagsQueryDTO queryDTO) {
        Specification<TagsEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (queryDTO.getId() != null) {
                predicates.add(cb.equal(root.get("id"), queryDTO.getId()));
            }
            if (queryDTO.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), queryDTO.getTenantId()));
            }
            if (queryDTO.getType() != null) {
                predicates.add(cb.equal(root.get("type"), queryDTO.getType()));
            }
            if (StringUtils.hasText(queryDTO.getName())) {
                predicates.add(cb.equal(root.get("name"), queryDTO.getName()));
            }
            if (StringUtils.hasText(queryDTO.getNameLike())) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getNameLike() + "%"));
            }
            if (StringUtils.hasText(queryDTO.getSlug())) {
                predicates.add(cb.equal(root.get("slug"), queryDTO.getSlug()));
            }
            if (StringUtils.hasText(queryDTO.getDescription())) {
                predicates.add(cb.equal(root.get("description"), queryDTO.getDescription()));
            }
            if (queryDTO.getIsSystem() != null) {
                predicates.add(cb.equal(root.get("isSystem"), queryDTO.getIsSystem()));
            }
            if (queryDTO.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), queryDTO.getIsActive()));
            }
            if (queryDTO.getCreatedAt() != null) {
                predicates.add(cb.equal(root.get("createdAt"), queryDTO.getCreatedAt()));
            }
            if (queryDTO.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), queryDTO.getCreatedFrom()));
            }
            if (queryDTO.getCreatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), queryDTO.getCreatedTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort;
        if (StringUtils.hasText(queryDTO.getSortBy())) {
            Sort.Direction direction = "asc".equalsIgnoreCase(queryDTO.getSortOrder()) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(direction, queryDTO.getSortBy());
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        int pageNumber = (queryDTO.getPage() != null && queryDTO.getPage() > 0) ? queryDTO.getPage() - 1 : 0;
        int pageSize = (queryDTO.getPageSize() != null && queryDTO.getPageSize() > 0) ? queryDTO.getPageSize() : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        return tagsRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional
    public TagsEntity create(TagsCreateDTO createDTO) {
        // 基础校验已由 @Valid 处理，这里补充业务规则和默认值。
        TagsEntity entity = new TagsEntity();
        entity.setTenantId(createDTO.getTenantId());
        entity.setType(createDTO.getType());
        entity.setName(createDTO.getName());
        entity.setSlug(createDTO.getSlug());
        entity.setDescription(createDTO.getDescription());
        entity.setIsSystem(Boolean.TRUE.equals(createDTO.getIsSystem()));
        entity.setIsActive(Boolean.TRUE.equals(createDTO.getIsActive()));

        // created_at 系统填写；SQL 默认 CURRENT_TIMESTAMP(3)，这里同样在应用层填充以便立即返回。
        entity.setCreatedAt(LocalDateTime.now());

        // 唯一性校验：tenantId + type + slug
        if (tagsRepository.findByTenantIdAndTypeAndSlug(entity.getTenantId(), entity.getType(), entity.getSlug()).isPresent()) {
            throw new IllegalArgumentException("标签已存在（tenantId+type+slug 唯一）。");
        }

        return tagsRepository.save(entity);
    }

    @Override
    @Transactional
    public TagsEntity update(TagsUpdateDTO updateDTO) {
        TagsEntity entity = tagsRepository.findById(updateDTO.getId())
                .orElseThrow(() -> new RuntimeException("Tag not found with id: " + updateDTO.getId()));

        if (updateDTO.getTenantId() != null && updateDTO.getTenantId().isPresent()) {
            entity.setTenantId(updateDTO.getTenantId().get());
        }
        if (updateDTO.getType() != null && updateDTO.getType().isPresent()) {
            entity.setType(updateDTO.getType().get());
        }
        if (updateDTO.getName() != null && updateDTO.getName().isPresent()) {
            entity.setName(updateDTO.getName().get());
        }
        if (updateDTO.getSlug() != null && updateDTO.getSlug().isPresent()) {
            entity.setSlug(updateDTO.getSlug().get());
        }
        if (updateDTO.getDescription() != null && updateDTO.getDescription().isPresent()) {
            entity.setDescription(updateDTO.getDescription().get());
        }
        if (updateDTO.getIsSystem() != null && updateDTO.getIsSystem().isPresent()) {
            entity.setIsSystem(updateDTO.getIsSystem().get());
        }
        if (updateDTO.getIsActive() != null && updateDTO.getIsActive().isPresent()) {
            entity.setIsActive(updateDTO.getIsActive().get());
        }

        // createdAt 只读：即使前端传入也忽略（DTO 已标注只读）

        // 唯一性校验（排除自身）
        tagsRepository.findByTenantIdAndTypeAndSlug(entity.getTenantId(), entity.getType(), entity.getSlug())
                .ifPresent(exists -> {
                    if (!exists.getId().equals(entity.getId())) {
                        throw new IllegalArgumentException("标签已存在（tenantId+type+slug 唯一）。");
                    }
                });

        return tagsRepository.save(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!tagsRepository.existsById(id)) {
            throw new RuntimeException("Tag not found with id: " + id);
        }
        tagsRepository.deleteById(id);
    }
}

