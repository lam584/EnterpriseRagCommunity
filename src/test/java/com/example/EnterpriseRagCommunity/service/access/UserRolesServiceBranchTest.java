package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.UserRolesCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UserRolesQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.UserRolesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRolesEntity;
import com.example.EnterpriseRagCommunity.repository.access.UserRolesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRolesServiceBranchTest {

    @Test
    void create_update_query_get_delete_should_cover_branches() {
        UserRolesRepository repo = mock(UserRolesRepository.class);
        when(repo.save(any(UserRolesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        UserRolesEntity found = new UserRolesEntity();
        found.setId(1L);
        found.setRoles("old");
        found.setCanLogin(false);
        when(repo.findById(1L)).thenReturn(Optional.of(found));
        when(repo.findById(2L)).thenReturn(Optional.empty());
        when(repo.findAll()).thenReturn(List.of(found));

        UserRolesService s = new UserRolesService(repo);

        UserRolesCreateDTO create = new UserRolesCreateDTO();
        create.setTenantId(10L);
        create.setRoles("r1");
        create.setCanLogin(true);
        UserRolesEntity created = s.create(create);
        assertEquals("r1", created.getRoles());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());

        UserRolesUpdateDTO update = new UserRolesUpdateDTO();
        update.setId(1L);
        update.setRoles(" ");
        update.setCanLogin(true);
        update.setCanComment(true);
        update.setNotes("n");
        UserRolesEntity updated = s.update(update);
        assertEquals("old", updated.getRoles());
        assertEquals(true, updated.getCanLogin());
        assertEquals(true, updated.getCanComment());
        assertEquals("n", updated.getNotes());
        assertNotNull(updated.getUpdatedAt());

        UserRolesUpdateDTO miss = new UserRolesUpdateDTO();
        miss.setId(2L);
        assertThrows(RuntimeException.class, () -> s.update(miss));

        UserRolesQueryDTO query = new UserRolesQueryDTO();
        query.setPageNum(1);
        query.setPageSize(20);
        query.setTenantId(10L);
        query.setRoles("r");
        query.setCanLogin(true);
        assertEquals(0, s.query(query).getTotalElements());

        assertEquals(found, s.getById(1L));
        assertThrows(RuntimeException.class, () -> s.getById(2L));
        assertEquals(1, s.getAll().size());

        s.delete(99L);
        verify(repo).deleteById(99L);
    }
}
