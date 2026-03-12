package com.example.EnterpriseRagCommunity.controller.debug;

import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessDebugControllerBranchTest {

    @Mock
    private UsersRepository usersRepository;
    @Mock
    private UserRoleLinksRepository userRoleLinksRepository;
    @Mock
    private UserRolesRepository userRolesRepository;
    @Mock
    private RolePermissionsRepository rolePermissionsRepository;
    @Mock
    private PermissionsRepository permissionsRepository;
    @Mock
    private AccessControlService accessControlService;

    @Test
    void snapshot_shouldReturnAnonymousWhenAuthenticationMissing() {
        AccessDebugController controller = new AccessDebugController(
                usersRepository,
                userRoleLinksRepository,
                userRolesRepository,
                rolePermissionsRepository,
                permissionsRepository,
                accessControlService
        );

        ResponseEntity<?> response = controller.snapshot(null);

        assertEquals(200, response.getStatusCode().value());
        AccessDebugController.SnapshotResponse body = (AccessDebugController.SnapshotResponse) response.getBody();
        assertNotNull(body);
        assertNull(body.email());
        assertFalse(body.authenticated());
        assertEquals(List.of(), body.authenticationAuthorities());
        assertNull(body.db());
        verifyNoInteractions(usersRepository, userRoleLinksRepository, userRolesRepository, rolePermissionsRepository, permissionsRepository, accessControlService);
    }

    @Test
    void snapshot_shouldReturnEmptyDbWhenUserNotFound() {
        AccessDebugController controller = new AccessDebugController(
                usersRepository,
                userRoleLinksRepository,
                userRolesRepository,
                rolePermissionsRepository,
                permissionsRepository,
                accessControlService
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken("dev@example.com", "p", List.of(new SimpleGrantedAuthority("ROLE_DEV")));
        when(usersRepository.findByEmailAndIsDeletedFalse("dev@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.snapshot(authentication);

        AccessDebugController.SnapshotResponse body = (AccessDebugController.SnapshotResponse) response.getBody();
        assertNotNull(body);
        assertEquals("dev@example.com", body.email());
        assertTrue(body.authenticated());
        assertEquals(List.of("ROLE_DEV"), body.authenticationAuthorities());
        assertNotNull(body.db());
        assertNull(body.db().userId());
        assertEquals(List.of(), body.db().roleIdsFromUserRoleLinks());
        assertEquals(List.of(), body.db().roleNamesFromUserRoles());
        assertEquals(List.of(), body.db().roleNamesFromRolePermissions());
        assertEquals(List.of(), body.db().allowedPermissionKeys());
        assertEquals(List.of(), body.db().builtAuthoritiesFromAccessControlService());
        verify(usersRepository).findByEmailAndIsDeletedFalse("dev@example.com");
        verifyNoInteractions(userRoleLinksRepository, userRolesRepository, rolePermissionsRepository, permissionsRepository, accessControlService);
    }

    @Test
    void snapshot_shouldCoverEmptyRoleIdsPath() {
        AccessDebugController controller = new AccessDebugController(
                usersRepository,
                userRoleLinksRepository,
                userRolesRepository,
                rolePermissionsRepository,
                permissionsRepository,
                accessControlService
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken("u1@example.com", "p", List.of((GrantedAuthority) () -> null));
        UsersEntity user = new UsersEntity();
        user.setId(11L);
        when(usersRepository.findByEmailAndIsDeletedFalse("u1@example.com")).thenReturn(Optional.of(user));
        when(userRoleLinksRepository.findByUserId(11L)).thenReturn(List.of());
        when(accessControlService.buildAuthorities(11L)).thenReturn(List.of());

        ResponseEntity<?> response = controller.snapshot(authentication);

        AccessDebugController.SnapshotResponse body = (AccessDebugController.SnapshotResponse) response.getBody();
        assertNotNull(body);
        assertEquals("u1@example.com", body.email());
        assertEquals(List.of(), body.authenticationAuthorities());
        assertNotNull(body.db());
        assertEquals(11L, body.db().userId());
        assertEquals(List.of(), body.db().roleIdsFromUserRoleLinks());
        assertEquals(List.of(), body.db().roleNamesFromUserRoles());
        assertEquals(List.of(), body.db().roleNamesFromRolePermissions());
        assertEquals(List.of(), body.db().allowedPermissionKeys());
        assertEquals(List.of(), body.db().builtAuthoritiesFromAccessControlService());
        verify(userRolesRepository, never()).findAllById(any());
        verify(rolePermissionsRepository, never()).findByRoleId(any());
        verify(permissionsRepository, never()).findAllById(any());
        verify(accessControlService).buildAuthorities(11L);
    }

    @Test
    void snapshot_shouldCoverAllowDenyAndRoleNameBranches() {
        AccessDebugController controller = new AccessDebugController(
                usersRepository,
                userRoleLinksRepository,
                userRolesRepository,
                rolePermissionsRepository,
                permissionsRepository,
                accessControlService
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "full@example.com",
                "p",
                List.of(new SimpleGrantedAuthority("ROLE_DEV"), (GrantedAuthority) () -> null)
        );
        UsersEntity user = new UsersEntity();
        user.setId(22L);
        when(usersRepository.findByEmailAndIsDeletedFalse("full@example.com")).thenReturn(Optional.of(user));

        UserRoleLinksEntity link1 = new UserRoleLinksEntity();
        link1.setUserId(22L);
        link1.setRoleId(1L);
        UserRoleLinksEntity link2 = new UserRoleLinksEntity();
        link2.setUserId(22L);
        link2.setRoleId(2L);
        UserRoleLinksEntity link3 = new UserRoleLinksEntity();
        link3.setUserId(22L);
        link3.setRoleId(null);
        when(userRoleLinksRepository.findByUserId(22L)).thenReturn(List.of(link1, link2, link3));

        UserRolesEntity role1 = new UserRolesEntity();
        role1.setId(1L);
        role1.setRoles("ADMIN");
        UserRolesEntity role2 = new UserRolesEntity();
        role2.setId(2L);
        role2.setRoles("EDITOR");
        when(userRolesRepository.findAllById(any())).thenReturn(List.of(role1, role2));

        RolePermissionsEntity rpAllowDeniedByDeny = new RolePermissionsEntity();
        rpAllowDeniedByDeny.setRoleId(1L);
        rpAllowDeniedByDeny.setRoleName("管理员");
        rpAllowDeniedByDeny.setPermissionId(100L);
        rpAllowDeniedByDeny.setAllow(true);

        RolePermissionsEntity rpDeny = new RolePermissionsEntity();
        rpDeny.setRoleId(1L);
        rpDeny.setRoleName(" ");
        rpDeny.setPermissionId(100L);
        rpDeny.setAllow(false);

        RolePermissionsEntity rpAllowKept = new RolePermissionsEntity();
        rpAllowKept.setRoleId(2L);
        rpAllowKept.setRoleName(null);
        rpAllowKept.setPermissionId(101L);
        rpAllowKept.setAllow(true);

        when(rolePermissionsRepository.findByRoleId(1L)).thenReturn(List.of(rpAllowDeniedByDeny, rpDeny));
        when(rolePermissionsRepository.findByRoleId(2L)).thenReturn(List.of(rpAllowKept));

        PermissionsEntity p = new PermissionsEntity();
        p.setId(101L);
        p.setResource("post");
        p.setAction("read");
        when(permissionsRepository.findAllById(eq(Set.of(101L)))).thenReturn(List.of(p));
        when(accessControlService.buildAuthorities(22L)).thenReturn(List.of(new SimpleGrantedAuthority("post:read")));

        ResponseEntity<?> response = controller.snapshot(authentication);

        AccessDebugController.SnapshotResponse body = (AccessDebugController.SnapshotResponse) response.getBody();
        assertNotNull(body);
        assertEquals("full@example.com", body.email());
        assertEquals(List.of("ROLE_DEV"), body.authenticationAuthorities());
        assertNotNull(body.db());
        assertEquals(22L, body.db().userId());
        assertEquals(List.of(1L, 2L), body.db().roleIdsFromUserRoleLinks());
        assertEquals(List.of("1=ADMIN", "2=EDITOR"), body.db().roleNamesFromUserRoles());
        assertEquals(List.of("1=管理员"), body.db().roleNamesFromRolePermissions());
        assertEquals(List.of("post:read"), body.db().allowedPermissionKeys());
        assertEquals(List.of("post:read"), body.db().builtAuthoritiesFromAccessControlService());
        verify(rolePermissionsRepository).findByRoleId(1L);
        verify(rolePermissionsRepository).findByRoleId(2L);
        verify(permissionsRepository).findAllById(eq(Set.of(101L)));
        verify(accessControlService).buildAuthorities(22L);
    }
}
