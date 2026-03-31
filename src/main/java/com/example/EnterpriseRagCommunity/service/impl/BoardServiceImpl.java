package com.example.EnterpriseRagCommunity.service.impl;

import com.example.EnterpriseRagCommunity.dto.content.BoardsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.BoardService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardsRepository boardsRepository;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @Override
    public Page<BoardsDTO> queryBoards(BoardsQueryDTO queryDTO) {
        Specification<BoardsEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (queryDTO.getId() != null) {
                predicates.add(cb.equal(root.get("id"), queryDTO.getId()));
            }
            if (queryDTO.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), queryDTO.getTenantId()));
            }
            if (queryDTO.getParentId() != null) {
                predicates.add(cb.equal(root.get("parentId"), queryDTO.getParentId()));
            }
            if (StringUtils.hasText(queryDTO.getName())) {
                predicates.add(cb.equal(root.get("name"), queryDTO.getName()));
            }
            if (StringUtils.hasText(queryDTO.getNameLike())) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getNameLike() + "%"));
            }
            if (StringUtils.hasText(queryDTO.getDescription())) {
                predicates.add(cb.equal(root.get("description"), queryDTO.getDescription()));
            }
            if (queryDTO.getVisible() != null) {
                predicates.add(cb.equal(root.get("visible"), queryDTO.getVisible()));
            }
            if (queryDTO.getSortOrder() != null) {
                predicates.add(cb.equal(root.get("sortOrder"), queryDTO.getSortOrder()));
            }
            if (queryDTO.getSortOrderFrom() != null) {
                predicates.add(cb.ge(root.get("sortOrder"), queryDTO.getSortOrderFrom()));
            }
            if (queryDTO.getSortOrderTo() != null) {
                predicates.add(cb.le(root.get("sortOrder"), queryDTO.getSortOrderTo()));
            }
            if (queryDTO.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), queryDTO.getCreatedFrom()));
            }
            if (queryDTO.getCreatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), queryDTO.getCreatedTo()));
            }
            if (queryDTO.getUpdatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), queryDTO.getUpdatedFrom()));
            }
            if (queryDTO.getUpdatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("updatedAt"), queryDTO.getUpdatedTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort;
        if (StringUtils.hasText(queryDTO.getSortBy())) {
            Sort.Direction direction = Sort.Direction.ASC;
            if ("desc".equalsIgnoreCase(queryDTO.getSortOrderDirection())) {
                direction = Sort.Direction.DESC;
            }
            sort = Sort.by(direction, queryDTO.getSortBy());
        } else {
            // Default sort
            sort = Sort.by(Sort.Direction.ASC, "sortOrder");
        }

        // Handle page number: DTO is 1-based, Spring is 0-based
        int pageNumber = (queryDTO.getPage() != null && queryDTO.getPage() > 0) ? queryDTO.getPage() - 1 : 0;
        int pageSize = (queryDTO.getPageSize() != null && queryDTO.getPageSize() > 0) ? queryDTO.getPageSize() : 20;

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        Page<BoardsEntity> page = boardsRepository.findAll(spec, pageable);

        return page.map(this::convertToDTO);
    }

    @Override
    @Transactional
    public BoardsDTO createBoard(BoardsCreateDTO createDTO) {
        BoardsEntity entity = new BoardsEntity();
        entity.setTenantId(createDTO.getTenantId());
        entity.setParentId(createDTO.getParentId());
        entity.setName(createDTO.getName());
        entity.setDescription(createDTO.getDescription());
        entity.setVisible(createDTO.getVisible());
        entity.setSortOrder(createDTO.getSortOrder());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        // Handle parentId 0 as null if needed, or keep as is if DB allows 0.
        // Assuming 0 means root and DB expects null for root, or 0 is valid ID.
        // Based on DTO "为空表示顶级", if it comes as 0, we might want to treat it as null if DB FK requires it.
        // But let's assume standard behavior.
        if (createDTO.getParentId() != null && createDTO.getParentId() == 0) {
            entity.setParentId(null);
        }

        BoardsEntity saved = boardsRepository.save(entity);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "BOARD_CREATE",
                "BOARD",
                saved.getId(),
                AuditResult.SUCCESS,
                "创建版块",
                null,
                auditDiffBuilder.build(Map.of(), summarize(saved))
        );
        return convertToDTO(saved);
    }

    @Override
    @Transactional
    public BoardsDTO updateBoard(BoardsUpdateDTO updateDTO) {
        BoardsEntity entity = boardsRepository.findById(updateDTO.getId())
                .orElseThrow(() -> new RuntimeException("Board not found with id: " + updateDTO.getId()));
        Map<String, Object> before = summarize(entity);

        if (updateDTO.isHasTenantId()) {
            entity.setTenantId(updateDTO.getTenantId());
        }
        if (updateDTO.isHasParentId()) {
            Long pid = updateDTO.getParentId();
            entity.setParentId(pid == null || pid == 0 ? null : pid);
        }
        if (updateDTO.isHasName()) {
            String name = updateDTO.getName();
            if (name != null && name.length() > 64) {
                throw new IllegalArgumentException("板块名称长度不能超过64个字符");
            }
            entity.setName(name);
        }
        if (updateDTO.isHasDescription()) {
            String description = updateDTO.getDescription();
            if (description != null && description.length() > 255) {
                throw new IllegalArgumentException("板块描述长度不能超过255个字符");
            }
            entity.setDescription(description);
        }
        if (updateDTO.isHasVisible()) {
            entity.setVisible(updateDTO.getVisible());
        }
        if (updateDTO.isHasSortOrder()) {
            entity.setSortOrder(updateDTO.getSortOrder());
        }

        entity.setUpdatedAt(LocalDateTime.now());

        BoardsEntity saved = boardsRepository.save(entity);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "BOARD_UPDATE",
                "BOARD",
                saved.getId(),
                AuditResult.SUCCESS,
                "更新版块",
                null,
                auditDiffBuilder.build(before, summarize(saved))
        );
        return convertToDTO(saved);
    }

    @Override
    @Transactional
    public void deleteBoard(Long id) {
        BoardsEntity beforeEntity = boardsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Board not found with id: " + id));
        boardsRepository.deleteById(id);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "BOARD_DELETE",
                "BOARD",
                id,
                AuditResult.SUCCESS,
                "删除版块",
                null,
                auditDiffBuilder.build(summarize(beforeEntity), Map.of())
        );
    }

    private BoardsDTO convertToDTO(BoardsEntity entity) {
        BoardsDTO dto = new BoardsDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setParentId(entity.getParentId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setVisible(entity.getVisible());
        dto.setSortOrder(entity.getSortOrder());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private static Map<String, Object> summarize(BoardsEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (e == null) return m;
        m.put("id", e.getId());
        m.put("tenantId", e.getTenantId());
        m.put("parentId", e.getParentId());
        m.put("name", e.getName());
        m.put("description", e.getDescription());
        m.put("visible", e.getVisible());
        m.put("sortOrder", e.getSortOrder());
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
