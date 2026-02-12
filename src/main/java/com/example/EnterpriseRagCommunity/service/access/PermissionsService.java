package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.PermissionsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.PermissionsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.PermissionsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
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
    private final RbacAuditService rbacAuditService;
    private final RolePermissionsRepository rolePermissionsRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final UsersRepository usersRepository;

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
        rbacAuditService.record("PERMISSION_CREATE", "permissions", String.valueOf(savedEntity.getId()), null, convertToDTO(savedEntity));
        return convertToDTO(savedEntity);
    }

    @Transactional
    public PermissionsUpdateDTO update(PermissionsUpdateDTO updateDTO) {
        PermissionsEntity entity = permissionsRepository.findById(updateDTO.getId())
                .orElseThrow(() -> new EntityNotFoundException("Permission not found with id: " + updateDTO.getId()));
        PermissionsUpdateDTO before = convertToDTO(entity);
        
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
        rbacAuditService.record("PERMISSION_UPDATE", "permissions", String.valueOf(savedEntity.getId()), before, convertToDTO(savedEntity));
        if (!safeEq(before.getResource(), savedEntity.getResource()) || !safeEq(before.getAction(), savedEntity.getAction())) {
            touchUsersByPermissionId(savedEntity.getId());
        }
        return convertToDTO(savedEntity);
    }

    @Transactional
    public void delete(Long id) {
        PermissionsEntity entity = permissionsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found with id: " + id));
        PermissionsUpdateDTO before = convertToDTO(entity);
        touchUsersByPermissionId(id);
        permissionsRepository.deleteById(id);
        rbacAuditService.record("PERMISSION_DELETE", "permissions", String.valueOf(id), before, null);
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

    private void touchUsersByPermissionId(Long permissionId) {
        if (permissionId == null) return;
        try {
            var rps = rolePermissionsRepository.findByPermissionId(permissionId);
            if (rps == null || rps.isEmpty()) return;
            var roleIds = rps.stream().map(rp -> rp.getRoleId()).filter(java.util.Objects::nonNull).distinct().toList();
            if (roleIds.isEmpty()) return;
            var links = roleIds.stream()
                    .flatMap(roleId -> userRoleLinksRepository.findByRoleId(roleId).stream())
                    .toList();
            if (links.isEmpty()) return;
            var userIds = links.stream().map(l -> l.getUserId()).filter(java.util.Objects::nonNull).distinct().toList();
            if (userIds.isEmpty()) return;
            var users = usersRepository.findAllById(userIds);
            for (UsersEntity u : users) {
                long v = u.getAccessVersion() == null ? 0L : u.getAccessVersion();
                u.setAccessVersion(v + 1L);
                u.setUpdatedAt(java.time.LocalDateTime.now());
            }
            usersRepository.saveAll(users);
        } catch (Exception ignored) {
        }
    }

    private static boolean safeEq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }
}
