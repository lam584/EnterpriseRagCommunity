package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.UserBanActionRequest;
import com.example.EnterpriseRagCommunity.dto.access.UsersCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.TenantsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.UsersService;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.AuthenticationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsersControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextTestSupport.clear();
    }

    @Test
    void create_should_convert_tenantId_when_present_and_absent() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        UsersEntity e1 = new UsersEntity();
        e1.setId(1L);
        e1.setEmail("u1@example.com");
        e1.setUsername("u1");
        e1.setIsDeleted(false);
        e1.setCreatedAt(LocalDateTime.now());
        e1.setUpdatedAt(LocalDateTime.now());
        when(usersService.create(any())).thenReturn(e1);

        UsersUpdateDTO dto1 = controller.create(new UsersCreateDTO()).getBody();
        assertThat(dto1).isNotNull();
        assertThat(dto1.getTenantId()).isNull();

        TenantsEntity t = new TenantsEntity();
        t.setId(9L);
        UsersEntity e2 = new UsersEntity();
        e2.setId(2L);
        e2.setTenantId(t);
        e2.setEmail("u2@example.com");
        e2.setUsername("u2");
        e2.setIsDeleted(false);
        e2.setCreatedAt(LocalDateTime.now());
        e2.setUpdatedAt(LocalDateTime.now());
        when(usersService.create(any())).thenReturn(e2);

        UsersUpdateDTO dto2 = controller.create(new UsersCreateDTO()).getBody();
        assertThat(dto2).isNotNull();
        assertThat(dto2.getTenantId()).isEqualTo(9L);
    }

    @Test
    void ban_should_throw_when_not_logged_in() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        UserBanActionRequest req = new UserBanActionRequest();
        req.setReason("r");

        assertThatThrownBy(() -> controller.ban(1L, req))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void ban_should_throw_when_actor_not_found() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        SecurityContextTestSupport.setAuthenticatedEmail("actor@example.com");
        when(administratorService.findByUsername("actor@example.com")).thenReturn(Optional.empty());

        UserBanActionRequest req = new UserBanActionRequest();
        req.setReason("r");

        assertThatThrownBy(() -> controller.ban(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前用户不存在");
    }

    @Test
    void ban_should_use_email_as_actorName_when_username_blank() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        UsersEntity actor = new UsersEntity();
        actor.setId(10L);
        actor.setEmail("actor@example.com");
        actor.setUsername("  ");
        when(administratorService.findByUsername("actor@example.com")).thenReturn(Optional.of(actor));

        UsersEntity updated = new UsersEntity();
        updated.setId(1L);
        updated.setEmail("u@example.com");
        updated.setUsername("u");
        updated.setIsDeleted(false);
        updated.setCreatedAt(LocalDateTime.now());
        updated.setUpdatedAt(LocalDateTime.now());
        when(usersService.banUser(eq(1L), eq(10L), eq("actor@example.com"), eq("r"), eq("ADMIN_USERS"), eq(null)))
                .thenReturn(updated);

        SecurityContextTestSupport.setAuthenticatedEmail("actor@example.com");

        UserBanActionRequest req = new UserBanActionRequest();
        req.setReason("r");
        var res = controller.ban(1L, req);
        assertThat(res.getBody()).isNotNull();
        verify(usersService).banUser(eq(1L), eq(10L), eq("actor@example.com"), eq("r"), eq("ADMIN_USERS"), eq(null));
    }

    @Test
    void update_delete_and_hardDelete_should_pass_actor_id() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        UsersEntity actor = new UsersEntity();
        actor.setId(10L);
        actor.setEmail("actor@example.com");
        actor.setUsername("actor");
        when(administratorService.findByUsername("actor@example.com")).thenReturn(Optional.of(actor));

        UsersEntity updated = new UsersEntity();
        updated.setId(1L);
        updated.setEmail("u@example.com");
        updated.setUsername("u");
        updated.setIsDeleted(false);
        updated.setCreatedAt(LocalDateTime.now());
        updated.setUpdatedAt(LocalDateTime.now());
        when(usersService.update(any(), eq(10L))).thenReturn(updated);

        SecurityContextTestSupport.setAuthenticatedEmail("actor@example.com");

        UsersUpdateDTO req = new UsersUpdateDTO();
        req.setId(1L);
        var updateRes = controller.update(req);
        assertThat(updateRes.getBody()).isNotNull();
        verify(usersService).update(any(), eq(10L));

        controller.delete(1L);
        verify(usersService).delete(1L, 10L);

        controller.hardDelete(1L);
        verify(usersService).hardDelete(1L, 10L);
    }

    @Test
    void update_should_throw_when_not_logged_in() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        assertThatThrownBy(() -> controller.update(new UsersUpdateDTO()))
                .isInstanceOf(AuthenticationException.class);
        verify(usersService, never()).update(any(), any());
    }

    @Test
    void unban_should_use_username_as_actorName_when_present() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        UsersEntity actor = new UsersEntity();
        actor.setId(10L);
        actor.setEmail("actor@example.com");
        actor.setUsername("actor");
        when(administratorService.findByUsername("actor@example.com")).thenReturn(Optional.of(actor));

        UsersEntity updated = new UsersEntity();
        updated.setId(1L);
        updated.setEmail("u@example.com");
        updated.setUsername("u");
        updated.setIsDeleted(false);
        updated.setCreatedAt(LocalDateTime.now());
        updated.setUpdatedAt(LocalDateTime.now());
        when(usersService.unbanUser(eq(1L), eq(10L), eq("actor"), eq("r"))).thenReturn(updated);

        SecurityContextTestSupport.setAuthenticatedEmail("actor@example.com");

        UserBanActionRequest req = new UserBanActionRequest();
        req.setReason("r");
        var res = controller.unban(1L, req);
        assertThat(res.getBody()).isNotNull();
        verify(usersService).unbanUser(eq(1L), eq(10L), eq("actor"), eq("r"));
    }

    @Test
    void query_should_map_entities_to_dtos() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        UsersEntity e = new UsersEntity();
        e.setId(1L);
        e.setEmail("u@example.com");
        e.setUsername("u");
        e.setIsDeleted(false);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());

        when(usersService.query(any())).thenReturn(new PageImpl<>(List.of(e), PageRequest.of(0, 20), 1));
        var res = controller.query(new UsersQueryDTO());
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getContent()).hasSize(1);
        assertThat(res.getBody().getContent().get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getUserRoles_should_map_entities_to_roleLinkDto() {
        UsersService usersService = mock(UsersService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersController controller = new UsersController(usersService, administratorService);

        UserRoleLinksEntity link = new UserRoleLinksEntity();
        link.setUserId(1L);
        link.setRoleId(2L);
        link.setScopeType("GLOBAL");
        link.setScopeId(0L);
        link.setExpiresAt(null);

        when(usersService.getUserRoles(1L)).thenReturn(List.of(link));
        var res = controller.getUserRoles(1L);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).hasSize(1);
        assertThat(res.getBody().get(0).getUserId()).isEqualTo(1L);
        assertThat(res.getBody().get(0).getRoleId()).isEqualTo(2L);
    }
}
