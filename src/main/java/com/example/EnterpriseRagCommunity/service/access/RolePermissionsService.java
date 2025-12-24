package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.RolePermissionUpsertDTO;
import com.example.EnterpriseRagCommunity.dto.access.RolePermissionViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionId;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRolesRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RolePermissionsService {

    private final RolePermissionsRepository rolePermissionsRepository;
    private final UserRolesRepository userRolesRepository;
    private final PermissionsRepository permissionsRepository;

    private static RolePermissionViewDTO toView(RolePermissionsEntity e) {
        if (e == null) return null;
        return new RolePermissionViewDTO(e.getRoleId(), e.getPermissionId(), e.getAllow());
    }

    @Transactional(readOnly = true)
    public List<RolePermissionViewDTO> listByRoleId(Long roleId) {
        return rolePermissionsRepository.findByRoleId(roleId)
                .stream()
                .map(RolePermissionsService::toView)
                .toList();
    }

    @Transactional
    public RolePermissionViewDTO upsert(RolePermissionUpsertDTO dto) {
        if (!userRolesRepository.existsById(dto.getRoleId())) {
            throw new EntityNotFoundException("Role not found: " + dto.getRoleId());
        }
        if (!permissionsRepository.existsById(dto.getPermissionId())) {
            throw new EntityNotFoundException("Permission not found: " + dto.getPermissionId());
        }

        RolePermissionId id = new RolePermissionId();
        id.setRoleId(dto.getRoleId());
        id.setPermissionId(dto.getPermissionId());

        RolePermissionsEntity entity = rolePermissionsRepository.findById(id).orElseGet(() -> {
            RolePermissionsEntity e = new RolePermissionsEntity();
            e.setRoleId(dto.getRoleId());
            e.setPermissionId(dto.getPermissionId());
            return e;
        });

        entity.setAllow(dto.getAllow());
        RolePermissionsEntity saved = rolePermissionsRepository.save(entity);
        return toView(saved);
    }

    @Transactional
    public void delete(Long roleId, Long permissionId) {
        RolePermissionId id = new RolePermissionId();
        id.setRoleId(roleId);
        id.setPermissionId(permissionId);

        if (!rolePermissionsRepository.existsById(id)) {
            throw new EntityNotFoundException("RolePermission not found: roleId=" + roleId + ", permissionId=" + permissionId);
        }

        rolePermissionsRepository.deleteById(id);
    }
}
