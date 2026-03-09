package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.PermissionsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.PermissionsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.PermissionsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionsServiceBranchTest {

    @Test
    void query_create_update_delete_get_should_cover_main_branches() {
        PermissionsRepository permissionsRepository = mock(PermissionsRepository.class);
        RbacAuditService rbacAuditService = mock(RbacAuditService.class);
        RolePermissionsRepository rolePermissionsRepository = mock(RolePermissionsRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);

        when(permissionsRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(permissionsRepository.save(any(PermissionsEntity.class))).thenAnswer(inv -> {
            PermissionsEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });

        PermissionsEntity old = new PermissionsEntity();
        old.setId(1L);
        old.setResource("res1");
        old.setAction("read");
        old.setDescription("d1");
        when(permissionsRepository.findById(1L)).thenReturn(Optional.of(old));
        when(permissionsRepository.findById(2L)).thenReturn(Optional.empty());

        RolePermissionsEntity rp = new RolePermissionsEntity();
        rp.setRoleId(7L);
        when(rolePermissionsRepository.findByPermissionId(1L)).thenReturn(List.of(rp));
        when(rolePermissionsRepository.findByPermissionId(10L)).thenReturn(List.of());
        UserRoleLinksEntity link = new UserRoleLinksEntity();
        link.setUserId(99L);
        when(userRoleLinksRepository.findByRoleId(7L)).thenReturn(List.of(link));
        UsersEntity u = new UsersEntity();
        u.setId(99L);
        u.setAccessVersion(2L);
        u.setUpdatedAt(LocalDateTime.now().minusDays(1));
        when(usersRepository.findAllById(List.of(99L))).thenReturn(List.of(u));

        PermissionsService s = new PermissionsService(
                permissionsRepository,
                rbacAuditService,
                rolePermissionsRepository,
                userRoleLinksRepository,
                usersRepository
        );

        PermissionsQueryDTO q = new PermissionsQueryDTO();
        q.setPageNum(1);
        q.setPageSize(20);
        q.setOrderBy("id");
        q.setSort("desc");
        q.setId(1L);
        q.setResource("r");
        q.setAction("a");
        q.setDescription("d");
        assertEquals(0, s.query(q).getTotalElements());

        PermissionsCreateDTO create = new PermissionsCreateDTO();
        create.setResource("r1");
        create.setAction("a1");
        create.setDescription("d1");
        PermissionsUpdateDTO created = s.create(create);
        assertEquals("r1", created.getResource());

        PermissionsUpdateDTO update = new PermissionsUpdateDTO();
        update.setId(1L);
        update.setDescription("d2");
        PermissionsUpdateDTO after = s.update(update);
        assertEquals("res1", after.getResource());
        assertEquals("read", after.getAction());
        verify(usersRepository, never()).saveAll(any());

        PermissionsUpdateDTO updateChangeKey = new PermissionsUpdateDTO();
        updateChangeKey.setId(1L);
        updateChangeKey.setResource("res2");
        s.update(updateChangeKey);
        verify(usersRepository).saveAll(any());
        assertEquals(3L, u.getAccessVersion());
        assertNotNull(u.getUpdatedAt());

        s.delete(1L);
        verify(permissionsRepository).deleteById(1L);

        assertEquals(1L, s.getById(1L).getId());
        assertThrows(EntityNotFoundException.class, () -> s.getById(2L));
        assertThrows(EntityNotFoundException.class, () -> s.update(new PermissionsUpdateDTO() {{
            setId(2L);
        }}));
        assertThrows(EntityNotFoundException.class, () -> s.delete(2L));
    }

    @Test
    void touchUsersByPermission_should_swallow_repository_exceptions() {
        PermissionsRepository permissionsRepository = mock(PermissionsRepository.class);
        RolePermissionsRepository rolePermissionsRepository = mock(RolePermissionsRepository.class);
        doThrow(new RuntimeException("x")).when(rolePermissionsRepository).findByPermissionId(3L);

        PermissionsEntity entity = new PermissionsEntity();
        entity.setId(3L);
        entity.setResource("r");
        entity.setAction("a");
        when(permissionsRepository.findById(3L)).thenReturn(Optional.of(entity));

        PermissionsService s = new PermissionsService(
                permissionsRepository,
                mock(RbacAuditService.class),
                rolePermissionsRepository,
                mock(UserRoleLinksRepository.class),
                mock(UsersRepository.class)
        );

        s.delete(3L);
        verify(permissionsRepository).deleteById(3L);
    }
}
