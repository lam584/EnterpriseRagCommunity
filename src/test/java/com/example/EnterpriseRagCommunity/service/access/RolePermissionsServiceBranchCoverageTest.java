package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.RolePermissionUpsertDTO;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionId;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

class RolePermissionsServiceBranchCoverageTest {

    @Test
    void createRoleWithMatrix_and_listRoles_should_cover_happy_paths() {
        RolePermissionsRepository roleRepo = mock(RolePermissionsRepository.class);
        PermissionsRepository permRepo = mock(PermissionsRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        RbacAuditService rbacAuditService = mock(RbacAuditService.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);

        when(permRepo.countByIdIn(any(Set.class))).thenReturn(2L);
        when(roleRepo.findMaxRoleId()).thenReturn(null);
        when(roleRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rolesRepository.findById(1L)).thenReturn(Optional.empty());
        when(rolesRepository.save(any(RolesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rolesRepository.listAll()).thenReturn(List.of());
        RolePermissionsRepository.RoleSummaryView v = mock(RolePermissionsRepository.RoleSummaryView.class);
        when(v.getRoleId()).thenReturn(8L);
        when(v.getRoleName()).thenReturn("  reviewer  ");
        when(roleRepo.findRoleSummaries()).thenReturn(List.of(v));

        RolePermissionsService svc = new RolePermissionsService(
                roleRepo, permRepo, userRoleLinksRepository, usersRepository, rbacAuditService, rolesRepository
        );

        RolePermissionUpsertDTO d1 = new RolePermissionUpsertDTO();
        d1.setPermissionId(11L);
        d1.setAllow(true);
        d1.setRoleName(" Admin ");
        RolePermissionUpsertDTO d2 = new RolePermissionUpsertDTO();
        d2.setPermissionId(12L);
        d2.setAllow(false);
        d2.setRoleName("ignored");
        var out = svc.createRoleWithMatrix(List.of(d1, d2));
        assertEquals(2, out.size());
        assertEquals(1L, out.get(0).getRoleId());

        var roles = svc.listRoles();
        assertEquals(1, roles.size());
        assertEquals(8L, roles.get(0).roleId());
        assertEquals("reviewer", roles.get(0).roleName());
    }

    @Test
    void replace_upsert_delete_clear_should_cover_touch_and_exceptions() {
        RolePermissionsRepository roleRepo = mock(RolePermissionsRepository.class);
        PermissionsRepository permRepo = mock(PermissionsRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        RbacAuditService rbacAuditService = mock(RbacAuditService.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);

        when(roleRepo.findByRoleId(3L)).thenReturn(List.of());
        when(roleRepo.deleteAllByRoleId(3L)).thenReturn(0);
        when(permRepo.countByIdIn(any(Set.class))).thenReturn(1L);
        when(roleRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(permRepo.existsById(9L)).thenReturn(true);
        when(roleRepo.findById(any(RolePermissionId.class))).thenReturn(Optional.empty(), Optional.of(new RolePermissionsEntity()));
        when(roleRepo.save(any(RolePermissionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rolesRepository.findById(anyLong())).thenReturn(Optional.of(new RolesEntity()));
        when(rolesRepository.save(any(RolesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRoleLinksRepository.findByRoleId(3L)).thenReturn(List.of());
        when(userRoleLinksRepository.findByRoleId(5L)).thenReturn(List.of(link(10L, 5L)));
        when(usersRepository.findAllById(List.of(10L))).thenReturn(List.of(user(10L, 1L)));
        when(usersRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        RolePermissionsService svc = new RolePermissionsService(
                roleRepo, permRepo, userRoleLinksRepository, usersRepository, rbacAuditService, rolesRepository
        );

        assertTrue(svc.replaceAllForRole(3L, List.of()).isEmpty());

        RolePermissionUpsertDTO up = new RolePermissionUpsertDTO();
        up.setRoleId(5L);
        up.setPermissionId(9L);
        up.setAllow(true);
        up.setRoleName(" Ops ");
        svc.upsert(up);

        RolePermissionUpsertDTO bad = new RolePermissionUpsertDTO();
        bad.setRoleId(null);
        bad.setPermissionId(9L);
        bad.setAllow(true);
        assertThrows(EntityNotFoundException.class, () -> svc.upsert(bad));

        RolePermissionsEntity existing = new RolePermissionsEntity();
        existing.setRoleId(5L);
        existing.setPermissionId(9L);
        when(roleRepo.findById(any(RolePermissionId.class))).thenReturn(Optional.of(existing));
        svc.delete(5L, 9L);
        when(roleRepo.findById(any(RolePermissionId.class))).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> svc.delete(5L, 9L));

        assertThrows(IllegalArgumentException.class, () -> svc.clearRole(null));
        svc.clearRole(3L);
        verify(roleRepo, atLeastOnce()).deleteAllByRoleId(3L);
    }

    @Test
    void touchUsers_failure_should_be_swallowed() {
        RolePermissionsRepository roleRepo = mock(RolePermissionsRepository.class);
        PermissionsRepository permRepo = mock(PermissionsRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);
        when(roleRepo.findByRoleId(6L)).thenReturn(List.of());
        when(roleRepo.deleteAllByRoleId(6L)).thenReturn(0);
        when(userRoleLinksRepository.findByRoleId(6L)).thenThrow(new RuntimeException("x"));

        RolePermissionsService svc = new RolePermissionsService(
                roleRepo, permRepo, userRoleLinksRepository, usersRepository, mock(RbacAuditService.class), rolesRepository
        );
        svc.clearRole(6L);

        when(roleRepo.findByRoleId(7L)).thenReturn(List.of());
        when(roleRepo.deleteAllByRoleId(7L)).thenReturn(0);
        when(userRoleLinksRepository.findByRoleId(7L)).thenReturn(List.of(link(null, 7L)));
        svc.clearRole(7L);

        when(userRoleLinksRepository.findByRoleId(7L)).thenReturn(List.of(link(9L, 7L)));
        when(usersRepository.findAllById(List.of(9L))).thenReturn(List.of());
        svc.clearRole(7L);
    }

    @Test
    void replace_and_create_should_cover_validation_edges() {
        RolePermissionsRepository roleRepo = mock(RolePermissionsRepository.class);
        PermissionsRepository permRepo = mock(PermissionsRepository.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);
        when(roleRepo.findByRoleId(1L)).thenReturn(List.of());
        when(permRepo.countByIdIn(any(Set.class))).thenReturn(0L);
        when(rolesRepository.listAll()).thenReturn(Arrays.asList(null, role(1L, " ")));
        RolePermissionsRepository.RoleSummaryView view = mock(RolePermissionsRepository.RoleSummaryView.class);
        when(view.getRoleId()).thenReturn(2L);
        when(view.getRoleName()).thenReturn(" ");
        when(roleRepo.findRoleSummaries()).thenReturn(List.of(view));

        RolePermissionsService svc = new RolePermissionsService(
                roleRepo, permRepo, mock(UserRoleLinksRepository.class), mock(UsersRepository.class), mock(RbacAuditService.class), rolesRepository
        );

        RolePermissionUpsertDTO bad1 = new RolePermissionUpsertDTO();
        bad1.setAllow(true);
        assertThrows(IllegalArgumentException.class, () -> svc.replaceAllForRole(1L, List.of(bad1)));

        RolePermissionUpsertDTO bad2 = new RolePermissionUpsertDTO();
        bad2.setPermissionId(1L);
        assertThrows(IllegalArgumentException.class, () -> svc.replaceAllForRole(1L, List.of(bad2)));

        RolePermissionUpsertDTO bad3 = new RolePermissionUpsertDTO();
        bad3.setPermissionId(1L);
        bad3.setAllow(true);
        bad3.setRoleName("x");
        assertThrows(EntityNotFoundException.class, () -> svc.createRoleWithMatrix(List.of(bad3)));

        assertTrue(svc.listRoles().isEmpty());
    }

    @Test
    void upsert_and_create_should_cover_more_guard_branches() {
        RolePermissionsRepository roleRepo = mock(RolePermissionsRepository.class);
        PermissionsRepository permRepo = mock(PermissionsRepository.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);
        when(permRepo.existsById(100L)).thenReturn(false);

        RolePermissionsService svc = new RolePermissionsService(
                roleRepo, permRepo, mock(UserRoleLinksRepository.class), mock(UsersRepository.class), mock(RbacAuditService.class), rolesRepository
        );

        RolePermissionUpsertDTO up = new RolePermissionUpsertDTO();
        up.setRoleId(9L);
        up.setPermissionId(100L);
        up.setAllow(true);
        assertThrows(EntityNotFoundException.class, () -> svc.upsert(up));

        assertThrows(IllegalArgumentException.class, () -> svc.replaceAllForRole(null, List.of()));

        RolePermissionUpsertDTO onlyNullRoleName = new RolePermissionUpsertDTO();
        onlyNullRoleName.setPermissionId(1L);
        onlyNullRoleName.setAllow(false);
        assertThrows(IllegalArgumentException.class, () -> svc.createRoleWithMatrix(Arrays.asList(null, onlyNullRoleName)));
    }

    private static UserRoleLinksEntity link(Long userId, Long roleId) {
        UserRoleLinksEntity l = new UserRoleLinksEntity();
        l.setUserId(userId);
        l.setRoleId(roleId);
        return l;
    }

    private static UsersEntity user(Long id, Long accessVersion) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        u.setAccessVersion(accessVersion);
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    private static RolesEntity role(Long id, String name) {
        RolesEntity r = new RolesEntity();
        r.setRoleId(id);
        r.setRoleName(name);
        return r;
    }
}
