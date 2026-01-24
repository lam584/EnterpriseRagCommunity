package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.PermissionsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.PermissionsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.PermissionsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionsService {

    private final PermissionsRepository permissionsRepository;

    @Transactional(readOnly = true)
    public Page<PermissionsUpdateDTO> query(PermissionsQueryDTO queryDTO) {
        int page = queryDTO.getPageNum() != null ? queryDTO.getPageNum() - 1 : 0;
        int size = queryDTO.getPageSize() != null ? queryDTO.getPageSize() : 20;
        
        Sort sort = Sort.unsorted();
        if (StringUtils.hasText(queryDTO.getOrderBy())) {
            Sort.Direction direction = "desc".equalsIgnoreCase(queryDTO.getSort()) ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(direction, queryDTO.getOrderBy());
        }
        
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Specification<PermissionsEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            if (queryDTO.getId() != null) {
                predicates.add(cb.equal(root.get("id"), queryDTO.getId()));
            }
            if (StringUtils.hasText(queryDTO.getResource())) {
                predicates.add(cb.like(root.get("resource"), "%" + queryDTO.getResource() + "%"));
            }
            if (StringUtils.hasText(queryDTO.getAction())) {
                predicates.add(cb.like(root.get("action"), "%" + queryDTO.getAction() + "%"));
            }
            if (StringUtils.hasText(queryDTO.getDescription())) {
                predicates.add(cb.like(root.get("description"), "%" + queryDTO.getDescription() + "%"));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<PermissionsEntity> entityPage = permissionsRepository.findAll(spec, pageable);
        return entityPage.map(this::convertToDTO);
    }

    @Transactional
    public PermissionsUpdateDTO create(PermissionsCreateDTO createDTO) {
        PermissionsEntity entity = new PermissionsEntity();
        entity.setResource(createDTO.getResource());
        entity.setAction(createDTO.getAction());
        entity.setDescription(createDTO.getDescription());
        
        PermissionsEntity savedEntity = permissionsRepository.save(entity);
        return convertToDTO(savedEntity);
    }

    @Transactional
    public PermissionsUpdateDTO update(PermissionsUpdateDTO updateDTO) {
        PermissionsEntity entity = permissionsRepository.findById(updateDTO.getId())
                .orElseThrow(() -> new EntityNotFoundException("Permission not found with id: " + updateDTO.getId()));
        
        if (updateDTO.getResource() != null) {
            entity.setResource(updateDTO.getResource());
        }
        if (updateDTO.getAction() != null) {
            entity.setAction(updateDTO.getAction());
        }
        if (updateDTO.getDescription() != null) {
            entity.setDescription(updateDTO.getDescription());
        }
        
        PermissionsEntity savedEntity = permissionsRepository.save(entity);
        return convertToDTO(savedEntity);
    }

    @Transactional
    public void delete(Long id) {
        if (!permissionsRepository.existsById(id)) {
            throw new EntityNotFoundException("Permission not found with id: " + id);
        }
        permissionsRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PermissionsUpdateDTO getById(Long id) {
        PermissionsEntity entity = permissionsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found with id: " + id));
        return convertToDTO(entity);
    }

    private PermissionsUpdateDTO convertToDTO(PermissionsEntity entity) {
        PermissionsUpdateDTO dto = new PermissionsUpdateDTO();
        dto.setId(entity.getId());
        dto.setResource(entity.getResource());
        dto.setAction(entity.getAction());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}
