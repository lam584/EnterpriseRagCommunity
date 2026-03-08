package com.example.EnterpriseRagCommunity.service.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.dto.access.RolePermissionUpsertDTO;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionId;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolesEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;

class RolePermissionsServiceTest {
    @Test
    void replaceAllForRole_should_validate_role_id() {
        RolePermissionsService s = new RolePermissionsService(
                mock(RolePermissionsRepository.class),
                mock(PermissionsRepository.class),
                mock(UserRoleLinksRepository.class),
                mock(UsersRepository.class),
                mock(RbacAuditService.class),
                mock(RolesRepository.class)
        );
        assertThrows(IllegalArgumentException.class, () -> s.replaceAllForRole(null, List.of()));
    }

    @Test
    void replaceAllForRole_should_clear_when_empty_list() {
        RolePermissionsRepository rolePermissionsRepository = mock(RolePermissionsRepository.class);
        when(rolePermissionsRepository.findByRoleId(1L)).thenReturn(List.of());
        when(rolePermissionsRepository.deleteAllByRoleId(1L)).thenReturn(0);

        RolePermissionsService s = new RolePermissionsService(
                rolePermissionsRepository,
                mock(PermissionsRepository.class),
                mock(UserRoleLinksRepository.class),
                mock(UsersRepository.class),
                mock(RbacAuditService.class),
                mock(RolesRepository.class)
        );
        assertEquals(List.of(), s.replaceAllForRole(1L, List.of()));
        verify(rolePermissionsRepository).deleteAllByRoleId(1L);
    }

    @Test
    void replaceAllForRole_should_throw_when_permission_not_found() {
        RolePermissionsRepository rolePermissionsRepository = mock(RolePermissionsRepository.class);
        when(rolePermissionsRepository.findByRoleId(1L)).thenReturn(List.of());

        PermissionsRepository permissionsRepository = mock(PermissionsRepository.class);
        when(permissionsRepository.countByIdIn(any(Set.class))).thenReturn(0L);

        RolePermissionsService s = new RolePermissionsService(
                rolePermissionsRepository,
                permissionsRepository,
                mock(UserRoleLinksRepository.class),
                mock(UsersRepository.class),
                mock(RbacAuditService.class),
                mock(RolesRepository.class)
        );

        RolePermissionUpsertDTO dto = new RolePermissionUpsertDTO();
        dto.setPermissionId(10L);
        dto.setAllow(true);
        dto.setRoleName("r1");
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> s.replaceAllForRole(1L, List.of(dto)));
    }

    @Test
    void createRoleWithMatrix_should_require_role_name() {
        RolePermissionsService s = new RolePermissionsService(
                mock(RolePermissionsRepository.class),
                mock(PermissionsRepository.class),
                mock(UserRoleLinksRepository.class),
                mock(UsersRepository.class),
                mock(RbacAuditService.class),
                mock(RolesRepository.class)
        );
        RolePermissionUpsertDTO dto = new RolePermissionUpsertDTO();
        dto.setPermissionId(1L);
        dto.setAllow(true);
        dto.setRoleName(" ");
        assertThrows(IllegalArgumentException.class, () -> s.createRoleWithMatrix(List.of(dto)));
    }

    @Test
    void upsert_should_create_new_entity_and_upsert_role_meta() {
        RolePermissionsRepository rolePermissionsRepository = mock(RolePermissionsRepository.class);
        when(rolePermissionsRepository.findById(any(RolePermissionId.class))).thenReturn(Optional.empty());
        when(rolePermissionsRepository.save(any(RolePermissionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PermissionsRepository permissionsRepository = mock(PermissionsRepository.class);
        when(permissionsRepository.existsById(9L)).thenReturn(true);

        RolesRepository rolesRepository = mock(RolesRepository.class);
        when(rolesRepository.findById(1L)).thenReturn(Optional.empty());
        when(rolesRepository.save(any(RolesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RolePermissionsService s = new RolePermissionsService(
                rolePermissionsRepository,
                permissionsRepository,
                mock(UserRoleLinksRepository.class),
                mock(UsersRepository.class),
                mock(RbacAuditService.class),
                rolesRepository
        );

        RolePermissionUpsertDTO dto = new RolePermissionUpsertDTO();
        dto.setRoleId(1L);
        dto.setPermissionId(9L);
        dto.setAllow(true);
        dto.setRoleName("Admin");
        s.upsert(dto);

        verify(rolesRepository).save(any(RolesEntity.class));
        verify(rolePermissionsRepository).save(any(RolePermissionsEntity.class));
        verify(rolePermissionsRepository, never()).saveAll(any());
    }
}

