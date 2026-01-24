package com.example.EnterpriseRagCommunity.service.impl;

import com.example.EnterpriseRagCommunity.dto.content.BoardsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.BoardService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardsRepository boardsRepository;

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

        Sort sort = Sort.unsorted();
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
        BeanUtils.copyProperties(createDTO, entity);
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
        return convertToDTO(saved);
    }

    @Override
    @Transactional
    public BoardsDTO updateBoard(BoardsUpdateDTO updateDTO) {
        BoardsEntity entity = boardsRepository.findById(updateDTO.getId())
                .orElseThrow(() -> new RuntimeException("Board not found with id: " + updateDTO.getId()));

        if (updateDTO.getTenantId() != null && updateDTO.getTenantId().isPresent()) {
            entity.setTenantId(updateDTO.getTenantId().get());
        }
        if (updateDTO.getParentId() != null && updateDTO.getParentId().isPresent()) {
            Long pid = updateDTO.getParentId().get();
            entity.setParentId(pid == 0 ? null : pid);
        }
        if (updateDTO.getName() != null && updateDTO.getName().isPresent()) {
            String name = updateDTO.getName().get();
            if (name.length() > 64) {
                throw new IllegalArgumentException("板块名称长度不能超过64个字符");
            }
            entity.setName(name);
        }
        if (updateDTO.getDescription() != null && updateDTO.getDescription().isPresent()) {
            String description = updateDTO.getDescription().get();
            if (description.length() > 255) {
                throw new IllegalArgumentException("板块描述长度不能超过255个字符");
            }
            entity.setDescription(description);
        }
        if (updateDTO.getVisible() != null && updateDTO.getVisible().isPresent()) {
            entity.setVisible(updateDTO.getVisible().get());
        }
        if (updateDTO.getSortOrder() != null && updateDTO.getSortOrder().isPresent()) {
            entity.setSortOrder(updateDTO.getSortOrder().get());
        }

        entity.setUpdatedAt(LocalDateTime.now());

        BoardsEntity saved = boardsRepository.save(entity);
        return convertToDTO(saved);
    }

    @Override
    @Transactional
    public void deleteBoard(Long id) {
        if (!boardsRepository.existsById(id)) {
            throw new RuntimeException("Board not found with id: " + id);
        }
        boardsRepository.deleteById(id);
    }

    private BoardsDTO convertToDTO(BoardsEntity entity) {
        BoardsDTO dto = new BoardsDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
