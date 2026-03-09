package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.UsersCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersQueryDTO;
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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsersServiceBranchCoverageTest {

    @Test
    void create_update_delete_get_and_query_should_cover_core_branches() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("raw")).thenReturn("enc");
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(inv -> {
            UsersEntity u = inv.getArgument(0);
            if (u.getId() == null) u.setId(10L);
            return u;
        });
        when(usersRepository.findById(10L)).thenReturn(Optional.of(user(10L, false, AccountStatus.ACTIVE)));
        when(usersRepository.findById(11L)).thenReturn(Optional.empty());
        when(usersRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(usersRepository.existsById(10L)).thenReturn(true);
        when(rolesRepository.findAllById(List.of(1L))).thenReturn(List.of(role(1L)));
        when(userRoleLinksRepository.findByUserId(10L)).thenReturn(List.of());

        UsersService svc = new UsersService(
                usersRepository,
                userRoleLinksRepository,
                rolesRepository,
                mock(RbacAuditService.class),
                mock(AuditLogWriter.class),
                passwordEncoder,
                mock(CommentsRepository.class),
                mock(PostsRepository.class)
        );

        UsersCreateDTO create = new UsersCreateDTO();
        create.setEmail("a@b");
        create.setUsername("u");
        create.setPasswordHash("raw");
        create.setStatus(AccountStatus.ACTIVE);
        create.setMetadata(Map.of("k", "v"));
        create.setRoleIds(List.of(1L));
        UsersEntity created = svc.create(create);
        assertEquals(10L, created.getId());

        UsersUpdateDTO update = new UsersUpdateDTO();
        update.setId(10L);
        update.setEmail("n@b");
        update.setUsername("nu");
        update.setPasswordHash("raw");
        update.setStatus(AccountStatus.DISABLED);
        update.setIsDeleted(true);
        update.setMetadata(Map.of("m", "n"));
        UsersEntity updated = svc.update(update);
        assertEquals("n@b", updated.getEmail());
        assertEquals(AccountStatus.DISABLED, updated.getStatus());

        svc.delete(10L);
        assertThrows(RuntimeException.class, () -> svc.getById(11L));
        assertNotNull(svc.getById(10L));

        UsersQueryDTO q = new UsersQueryDTO();
        q.setPageNum(1);
        q.setPageSize(20);
        q.setEmail("e");
        q.setUsername("n");
        q.setStatus(List.of(AccountStatus.ACTIVE));
        q.setLastLoginFrom(LocalDateTime.now().minusDays(1));
        q.setLastLoginTo(LocalDateTime.now());
        q.setCreatedAfter(LocalDateTime.now().minusDays(10));
        q.setCreatedBefore(LocalDateTime.now());
        q.setIncludeDeleted(false);
        svc.query(q);
        UsersQueryDTO q2 = new UsersQueryDTO();
        q2.setPageNum(1);
        q2.setPageSize(10);
        q2.setIncludeDeleted(true);
        svc.query(q2);

        ArgumentCaptor<Specification<UsersEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(usersRepository, atLeastOnce()).findAll(specCaptor.capture(), any(Pageable.class));
        for (Specification<UsersEntity> spec : specCaptor.getAllValues()) {
            CriteriaEnv env = new CriteriaEnv();
            assertNotNull(spec.toPredicate(env.root, env.query, env.cb));
        }
    }

    @Test
    void hardDelete_ban_unban_assignRoles_should_cover_validation_branches() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity softDeleted = user(2L, true, AccountStatus.ACTIVE);
        UsersEntity active = user(3L, false, AccountStatus.ACTIVE);
        UsersEntity deleted = user(4L, true, AccountStatus.DELETED);
        when(usersRepository.findById(2L)).thenReturn(Optional.of(softDeleted));
        when(usersRepository.findById(3L)).thenReturn(Optional.of(active));
        when(usersRepository.findById(4L)).thenReturn(Optional.of(deleted));
        when(usersRepository.findById(8L)).thenThrow(new RuntimeException("db"));
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usersRepository.existsById(3L)).thenReturn(true);
        when(usersRepository.existsById(9L)).thenReturn(false);
        when(postsRepository.countByAuthorId(2L)).thenReturn(0L);
        when(commentsRepository.countByAuthorId(2L)).thenReturn(0L);
        when(postsRepository.countByAuthorId(3L)).thenReturn(1L);
        when(commentsRepository.countByAuthorId(3L)).thenReturn(0L);
        when(rolesRepository.findAllById(List.of(1L))).thenReturn(List.of(role(1L)));
        when(userRoleLinksRepository.findByUserId(3L)).thenReturn(List.of(link(3L, 2L)));

        UsersService svc = new UsersService(
                usersRepository,
                userRoleLinksRepository,
                rolesRepository,
                mock(RbacAuditService.class),
                auditLogWriter,
                mock(PasswordEncoder.class),
                commentsRepository,
                postsRepository
        );

        assertThrows(RuntimeException.class, () -> svc.hardDelete(null));
        assertThrows(RuntimeException.class, () -> svc.hardDelete(3L));
        svc.hardDelete(2L);

        assertThrows(IllegalArgumentException.class, () -> svc.banUser(null, 1L, "a", "r", "POST", 9L));
        assertThrows(IllegalArgumentException.class, () -> svc.banUser(3L, 1L, "a", " ", "POST", 9L));
        assertThrows(IllegalStateException.class, () -> svc.banUser(4L, 1L, "a", "x", "POST", 9L));
        UsersEntity b = svc.banUser(3L, 1L, "admin", " reason ", "POST", 1L);
        assertEquals(AccountStatus.DISABLED, b.getStatus());

        assertThrows(IllegalArgumentException.class, () -> svc.unbanUser(3L, 1L, "a", " "));
        assertThrows(IllegalStateException.class, () -> svc.unbanUser(4L, 1L, "a", "x"));
        UsersEntity ub = svc.unbanUser(3L, 1L, "admin", "ok");
        assertTrue(ub.getMetadata().containsKey("ban"));

        assertThrows(RuntimeException.class, () -> svc.assignRoles(9L, List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> svc.assignRoles(3L, null));
        assertThrows(IllegalArgumentException.class, () -> svc.assignRoles(3L, java.util.Arrays.asList(1L, null)));
        svc.assignRoles(3L, List.of(1L, 1L));
        svc.assignRoles(3L, List.of());
    }

    @Test
    void hardDelete_and_ban_unban_should_cover_additional_metadata_and_missing_role_branches() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);

        UsersEntity softDeletedWithRefs = user(20L, true, AccountStatus.ACTIVE);
        UsersEntity activeUser = user(21L, false, AccountStatus.ACTIVE);
        activeUser.setMetadata(Map.of("ban", Map.of("legacy", "x")));

        when(usersRepository.findById(20L)).thenReturn(Optional.of(softDeletedWithRefs));
        when(usersRepository.findById(21L)).thenReturn(Optional.of(activeUser));
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(postsRepository.countByAuthorId(20L)).thenReturn(0L);
        when(commentsRepository.countByAuthorId(20L)).thenReturn(2L);
        when(usersRepository.existsById(21L)).thenReturn(true);
        when(userRoleLinksRepository.findByUserId(21L)).thenReturn(List.of());
        when(rolesRepository.findAllById(List.of(5L, 6L))).thenReturn(List.of(role(5L)));

        UsersService svc = new UsersService(
                usersRepository,
                userRoleLinksRepository,
                rolesRepository,
                mock(RbacAuditService.class),
                mock(AuditLogWriter.class),
                mock(PasswordEncoder.class),
                commentsRepository,
                postsRepository
        );

        assertThrows(IllegalStateException.class, () -> svc.hardDelete(20L));
        assertThrows(IllegalArgumentException.class, () -> svc.assignRoles(21L, List.of(5L, 6L)));

        UsersEntity banned = svc.banUser(21L, 9L, "ops", "  ban reason  ", "   ", 7L);
        Map<String, Object> banMeta = (Map<String, Object>) banned.getMetadata().get("ban");
        assertEquals(true, banMeta.get("active"));
        assertFalse(banMeta.containsKey("sourceType"));
        assertEquals(7L, banMeta.get("sourceId"));

        banned.setStatus(AccountStatus.ACTIVE);
        UsersEntity unbanned = svc.unbanUser(21L, 10L, "ops2", "recover");
        Map<String, Object> unbanMeta = (Map<String, Object>) unbanned.getMetadata().get("ban");
        assertEquals(true, unbanMeta.containsKey("unbannedAt"));
        assertEquals("recover", unbanMeta.get("unbanReason"));
    }

    private static RolesEntity role(Long id) {
        RolesEntity r = new RolesEntity();
        r.setRoleId(id);
        r.setRoleName("r" + id);
        return r;
    }

    private static UsersEntity user(Long id, boolean deleted, AccountStatus status) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        u.setIsDeleted(deleted);
        u.setStatus(status);
        u.setAccessVersion(1L);
        u.setMetadata(Map.of());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    private static UserRoleLinksEntity link(Long userId, Long roleId) {
        UserRoleLinksEntity l = new UserRoleLinksEntity();
        l.setUserId(userId);
        l.setRoleId(roleId);
        return l;
    }

    private static final class CriteriaEnv {
        private final Root<UsersEntity> root = mock(Root.class);
        private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
        private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
        private final Path<Object> p = mock(Path.class);
        private final Predicate predicate = mock(Predicate.class);

        private CriteriaEnv() {
            lenient().when(root.get(anyString())).thenReturn(p);
            lenient().when(p.get(anyString())).thenReturn(p);
            lenient().when(p.in((java.util.Collection<?>) any(java.util.Collection.class))).thenReturn(predicate);
            lenient().when(cb.equal(any(), any())).thenReturn(predicate);
            lenient().when(cb.like(any(), anyString())).thenReturn(predicate);
            lenient().when(cb.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
            lenient().when(cb.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
            lenient().when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        }
    }
}
