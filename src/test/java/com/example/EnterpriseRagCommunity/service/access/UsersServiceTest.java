package com.example.EnterpriseRagCommunity.service.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.EnterpriseRagCommunity.dto.access.UsersUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.RolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;

class UsersServiceTest {
    @Test
    void hardDelete_should_require_soft_deleted_user() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setIsDeleted(false);
        when(usersRepository.findById(1L)).thenReturn(Optional.of(u));
        when(postsRepository.countByAuthorId(1L)).thenReturn(0L);
        when(commentsRepository.countByAuthorId(1L)).thenReturn(0L);

        UsersService s = new UsersService(
                usersRepository,
                userRoleLinksRepository,
                mock(RolesRepository.class),
                mock(RbacAuditService.class),
                mock(AuditLogWriter.class),
                mock(PasswordEncoder.class),
                commentsRepository,
                postsRepository
        );

        assertThrows(RuntimeException.class, () -> s.hardDelete(1L));
        verify(userRoleLinksRepository, never()).deleteByUserId(any());
    }

    @Test
    void hardDelete_should_throw_when_refs_exist() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setIsDeleted(true);
        when(usersRepository.findById(1L)).thenReturn(Optional.of(u));
        when(postsRepository.countByAuthorId(1L)).thenReturn(1L);
        when(commentsRepository.countByAuthorId(1L)).thenReturn(0L);

        UsersService s = new UsersService(
                usersRepository,
                userRoleLinksRepository,
                mock(RolesRepository.class),
                mock(RbacAuditService.class),
                mock(AuditLogWriter.class),
                mock(PasswordEncoder.class),
                commentsRepository,
                postsRepository
        );

        assertThrows(IllegalStateException.class, () -> s.hardDelete(1L));
        verify(userRoleLinksRepository, never()).deleteByUserId(any());
        verify(usersRepository, never()).delete(any(UsersEntity.class));
    }

    @Test
    void update_delete_hardDelete_and_ban_should_block_self_when_last_available_account() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);

        UsersEntity u = new UsersEntity();
        u.setId(7L);
        u.setIsDeleted(false);
        u.setStatus(AccountStatus.ACTIVE);
        when(usersRepository.findById(7L)).thenReturn(Optional.of(u));
        when(usersRepository.countByIsDeletedFalse()).thenReturn(1L, 1L, 1L, 1L, 2L);
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(postsRepository.countByAuthorId(7L)).thenReturn(0L);
        when(commentsRepository.countByAuthorId(7L)).thenReturn(0L);

        UsersService s = new UsersService(
                usersRepository,
                userRoleLinksRepository,
                mock(RolesRepository.class),
                mock(RbacAuditService.class),
                mock(AuditLogWriter.class),
                mock(PasswordEncoder.class),
                commentsRepository,
                postsRepository
        );

        UsersUpdateDTO disableSelf = new UsersUpdateDTO();
        disableSelf.setId(7L);
        disableSelf.setStatus(AccountStatus.DISABLED);
        assertThrows(IllegalStateException.class, () -> s.update(disableSelf, 7L));
        assertThrows(IllegalStateException.class, () -> s.delete(7L, 7L));
        assertThrows(IllegalStateException.class, () -> s.hardDelete(7L, 7L));
        assertThrows(IllegalStateException.class, () -> s.banUser(7L, 7L, "self", "reason", "ADMIN_USERS", null));

        UsersUpdateDTO renameOnly = new UsersUpdateDTO();
        renameOnly.setId(7L);
        renameOnly.setUsername("new-name");
        assertDoesNotThrow(() -> s.update(renameOnly, 7L));
    }

    @Test
    void banUser_should_update_status_and_metadata_and_write_audit() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UsersEntity u = new UsersEntity();
        u.setId(2L);
        u.setIsDeleted(false);
        u.setStatus(AccountStatus.ACTIVE);
        u.setMetadata(Map.of());
        when(usersRepository.findById(2L)).thenReturn(Optional.of(u));
        when(usersRepository.countByIsDeletedFalse()).thenReturn(2L);
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersService s = new UsersService(
                usersRepository,
                mock(UserRoleLinksRepository.class),
                mock(RolesRepository.class),
                mock(RbacAuditService.class),
                auditLogWriter,
                mock(PasswordEncoder.class),
                mock(CommentsRepository.class),
                mock(PostsRepository.class)
        );

        UsersEntity saved = s.banUser(2L, 1L, "a", " reason ", "POST", 9L);
        assertEquals(AccountStatus.DISABLED, saved.getStatus());
        assertTrue(saved.getSessionInvalidatedAt() != null);
        assertTrue(saved.getMetadata() != null);
        assertTrue(saved.getMetadata().toString().contains("ban"));
        verify(auditLogWriter).write(any(), any(), eq("USER_BAN"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unbanUser_should_not_force_active_when_current_status_not_disabled() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UsersEntity u = new UsersEntity();
        u.setId(2L);
        u.setIsDeleted(false);
        u.setStatus(AccountStatus.ACTIVE);
        u.setMetadata(Map.of());
        when(usersRepository.findById(2L)).thenReturn(Optional.of(u));
        when(usersRepository.countByIsDeletedFalse()).thenReturn(2L);
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UsersService s = new UsersService(
                usersRepository,
                mock(UserRoleLinksRepository.class),
                mock(RolesRepository.class),
                mock(RbacAuditService.class),
                mock(AuditLogWriter.class),
                mock(PasswordEncoder.class),
                mock(CommentsRepository.class),
                mock(PostsRepository.class)
        );

        UsersEntity saved = s.unbanUser(2L, 1L, "a", "ok");
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertTrue(saved.getMetadata().toString().contains("ban"));
    }

    @Test
    void assignRoles_should_throw_when_role_missing() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.existsById(1L)).thenReturn(true);

        RolesRepository rolesRepository = mock(RolesRepository.class);
        when(rolesRepository.findAllById(anyList())).thenReturn(List.of());

        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        when(userRoleLinksRepository.findByUserId(1L)).thenReturn(List.of());

        UsersService s = new UsersService(
                usersRepository,
                userRoleLinksRepository,
                rolesRepository,
                mock(RbacAuditService.class),
                mock(AuditLogWriter.class),
                mock(PasswordEncoder.class),
                mock(CommentsRepository.class),
                mock(PostsRepository.class)
        );

        assertThrows(IllegalArgumentException.class, () -> s.assignRoles(1L, List.of(9L)));
        verify(userRoleLinksRepository, never()).saveAll(any());
    }

    @Test
    void assignRoles_should_clear_when_role_ids_empty() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.existsById(1L)).thenReturn(true);

        RolesRepository rolesRepository = mock(RolesRepository.class);
        when(rolesRepository.findAllById(anyList())).thenReturn(List.of());

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setAccessVersion(1L);
        u.setUpdatedAt(LocalDateTime.now());
        when(usersRepository.findById(1L)).thenReturn(Optional.of(u));
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        UserRoleLinksEntity l = new UserRoleLinksEntity();
        l.setUserId(1L);
        l.setRoleId(2L);
        when(userRoleLinksRepository.findByUserId(1L)).thenReturn(List.of(l));

        UsersService s = new UsersService(
                usersRepository,
                userRoleLinksRepository,
                rolesRepository,
                mock(RbacAuditService.class),
                mock(AuditLogWriter.class),
                mock(PasswordEncoder.class),
                mock(CommentsRepository.class),
                mock(PostsRepository.class)
        );

        s.assignRoles(1L, List.of());
        ArgumentCaptor<UsersEntity> cap = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersRepository).save(cap.capture());
        verify(userRoleLinksRepository).deleteAll(any());
    }
}
