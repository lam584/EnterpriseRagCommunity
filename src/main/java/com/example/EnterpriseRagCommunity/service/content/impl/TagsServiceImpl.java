package com.example.EnterpriseRagCommunity.service.content.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.repository.content.PostTagRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.service.content.TagsService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagsServiceImpl implements TagsService {

    private final TagsRepository tagsRepository;
    private final PostTagRepository postTagRepository;

    private static final Pattern DEFAULT_SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern RISK_SLUG_PATTERN = Pattern.compile("^[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*$");

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
            if (StringUtils.hasText(queryDTO.getKeyword())) {
                String k = queryDTO.getKeyword().trim();
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get("name")), "%" + k.toLowerCase() + "%"));
                ors.add(cb.like(cb.lower(root.get("slug")), "%" + k.toLowerCase() + "%"));
                ors.add(cb.like(cb.lower(root.get("description")), "%" + k.toLowerCase() + "%"));
                ors.add(cb.like(cb.lower(root.get("type").as(String.class)), "%" + k.toLowerCase() + "%"));
                try {
                    Long id = Long.parseLong(k);
                    ors.add(cb.equal(root.get("id"), id));
                } catch (NumberFormatException ignored) {
                }
                predicates.add(cb.or(ors.toArray(new Predicate[0])));
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
        entity.setName(createDTO.getName() == null ? null : createDTO.getName().trim());
        entity.setSlug(createDTO.getSlug() == null ? null : createDTO.getSlug().trim());
        entity.setDescription(StringUtils.hasText(createDTO.getDescription()) ? createDTO.getDescription().trim() : null);
        entity.setIsSystem(Boolean.TRUE.equals(createDTO.getIsSystem()));
        entity.setIsActive(Boolean.TRUE.equals(createDTO.getIsActive()));
        entity.setThreshold(createDTO.getThreshold());

        // created_at 系统填写；SQL 默认 CURRENT_TIMESTAMP(3)，这里同样在应用层填充以便立即返回。
        entity.setCreatedAt(LocalDateTime.now());

        validateSlug(entity.getType(), entity.getSlug());

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

        if (updateDTO.getTenantId() != null) {
            entity.setTenantId(updateDTO.getTenantId());
        }
        if (updateDTO.getType() != null) {
            entity.setType(updateDTO.getType());
        }
        if (updateDTO.getName() != null) {
            entity.setName(updateDTO.getName().trim());
        }
        if (updateDTO.getSlug() != null) {
            entity.setSlug(updateDTO.getSlug().trim());
        }
        if (updateDTO.getDescription() != null) {
            entity.setDescription(StringUtils.hasText(updateDTO.getDescription()) ? updateDTO.getDescription().trim() : null);
        }
        if (updateDTO.getIsSystem() != null) {
            entity.setIsSystem(updateDTO.getIsSystem());
        }
        if (updateDTO.getIsActive() != null) {
            entity.setIsActive(updateDTO.getIsActive());
        }
        if (updateDTO.getThreshold() != null) {
            entity.setThreshold(updateDTO.getThreshold());
        }

        // createdAt 只读：即使前端传入也忽略（DTO 已标注只读）

        if (updateDTO.getSlug() != null) {
            validateSlug(entity.getType(), entity.getSlug());
        }

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
        TagsEntity entity = tagsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag not found with id: " + id));

        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new IllegalStateException("系统标签不可删除。");
        }
        if (postTagRepository.existsByTagId(id)) {
            throw new IllegalStateException("标签正在使用，无法删除。");
        }

        tagsRepository.deleteById(id);
    }

    static void validateSlug(TagType type, String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new IllegalArgumentException("Slug 不能为空。");
        }
        Pattern pattern = (type == TagType.RISK) ? RISK_SLUG_PATTERN : DEFAULT_SLUG_PATTERN;
        if (!pattern.matcher(slug).matches()) {
            if (type == TagType.RISK) {
                throw new IllegalArgumentException("Slug 必须为 kebab-case（中文/字母数字/短横线）。");
            }
            throw new IllegalArgumentException("Slug 必须为 kebab-case（小写字母/数字/短横线）。");
        }
    }
}

