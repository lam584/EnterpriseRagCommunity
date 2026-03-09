package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessControlServiceBranchTest {

    @Test
    void loadActiveUserByEmail_should_return_or_throw() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.findByEmailAndIsDeletedFalse("a@b.com")).thenReturn(Optional.of(new UsersEntity()));
        when(usersRepository.findByEmailAndIsDeletedFalse("x@b.com")).thenReturn(Optional.empty());

        AccessControlService s = new AccessControlService(
                usersRepository,
                mock(UserRoleLinksRepository.class),
                mock(RolePermissionsRepository.class),
                mock(PermissionsRepository.class),
                mock(RolesRepository.class)
        );

        assertTrue(s.loadActiveUserByEmail("a@b.com") != null);
        assertThrows(java.util.NoSuchElementException.class, () -> s.loadActiveUserByEmail("x@b.com"));
    }

    @Test
    void buildAuthorities_should_cover_roles_scopes_permissions_and_deny_modes() throws Exception {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UserRoleLinksRepository linksRepository = mock(UserRoleLinksRepository.class);
        RolePermissionsRepository rolePermissionsRepository = mock(RolePermissionsRepository.class);
        PermissionsRepository permissionsRepository = mock(PermissionsRepository.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);

        UserRoleLinksEntity l1 = new UserRoleLinksEntity();
        l1.setUserId(1L);
        l1.setRoleId(1L);
        l1.setScopeType(null);
        l1.setScopeId(null);

        UserRoleLinksEntity l2 = new UserRoleLinksEntity();
        l2.setUserId(1L);
        l2.setRoleId(2L);
        l2.setScopeType("board");
        l2.setScopeId(9L);

        UserRoleLinksEntity expired = new UserRoleLinksEntity();
        expired.setUserId(1L);
        expired.setRoleId(3L);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(linksRepository.findByUserId(1L)).thenReturn(Arrays.asList(null, l1, l2, expired));

        RolesEntity r1 = new RolesEntity();
        r1.setRoleId(1L);
        r1.setRoleName(" admin ");
        RolesEntity r2 = new RolesEntity();
        r2.setRoleId(2L);
        r2.setRoleName(" ");
        when(rolesRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(r1, r2));

        RolePermissionsEntity rpFallback = new RolePermissionsEntity();
        rpFallback.setRoleId(2L);
        rpFallback.setRoleName("Editor");
        rpFallback.setPermissionId(20L);
        rpFallback.setAllow(true);
        when(rolePermissionsRepository.findByRoleId(2L)).thenReturn(List.of(rpFallback));

        RolePermissionsEntity rp11 = new RolePermissionsEntity();
        rp11.setRoleId(1L);
        rp11.setPermissionId(10L);
        rp11.setAllow(true);
        RolePermissionsEntity rp21Allow = new RolePermissionsEntity();
        rp21Allow.setRoleId(2L);
        rp21Allow.setPermissionId(10L);
        rp21Allow.setAllow(true);
        RolePermissionsEntity rp21Deny = new RolePermissionsEntity();
        rp21Deny.setRoleId(2L);
        rp21Deny.setPermissionId(10L);
        rp21Deny.setAllow(false);
        RolePermissionsEntity rp22 = new RolePermissionsEntity();
        rp22.setRoleId(2L);
        rp22.setPermissionId(20L);
        rp22.setAllow(true);
        when(rolePermissionsRepository.findByRoleIdIn(Set.of(1L, 2L)))
                .thenReturn(List.of(rp11, rp21Allow, rp21Deny, rp22));

        PermissionsEntity p10 = new PermissionsEntity();
        p10.setId(10L);
        p10.setResource("post");
        p10.setAction("read");
        PermissionsEntity p20 = new PermissionsEntity();
        p20.setId(20L);
        p20.setResource("board");
        p20.setAction("manage");
        when(permissionsRepository.findAllById(Set.of(10L, 20L))).thenReturn(List.of(p10, p20));

        AccessControlService s = new AccessControlService(
                usersRepository, linksRepository, rolePermissionsRepository, permissionsRepository, rolesRepository
        );
        setDenyFirst(s, true);

        Set<String> authorities = s.buildAuthorities(1L).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_ID_1"));
        assertTrue(authorities.contains("ROLE_admin"));
        assertTrue(authorities.contains("ROLE_ID_2@BOARD:9"));
        assertTrue(authorities.contains("ROLE_Editor@BOARD:9"));
        assertTrue(authorities.contains("PERM_post:read"));
        assertTrue(authorities.contains("PERM_board:manage@BOARD:9"));
        assertTrue(authorities.stream().noneMatch(x -> x.equals("PERM_post:read@BOARD:9")));

        setDenyFirst(s, false);
        Set<String> authoritiesDenyOff = s.buildAuthorities(1L).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertTrue(authoritiesDenyOff.contains("PERM_post:read@BOARD:9"));

        assertEquals("x:y", AccessControlService.toPermissionKey(" x ", " y "));
        assertEquals(":y", AccessControlService.toPermissionKey(null, " y "));
    }

    private static void setDenyFirst(AccessControlService service, boolean value) throws Exception {
        Field field = AccessControlService.class.getDeclaredField("denyFirst");
        field.setAccessible(true);
        field.set(service, value);
    }
}
