package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionId;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "security.permissions.deny-first=true"
})
class AccessControlServiceTest {

    @Autowired
    private UsersRepository usersRepository;
    @Autowired
    private UserRolesRepository userRolesRepository;
    @Autowired
    private UserRoleLinksRepository userRoleLinksRepository;
    @Autowired
    private PermissionsRepository permissionsRepository;
    @Autowired
    private RolePermissionsRepository rolePermissionsRepository;
    @Autowired
    private AccessControlService accessControlService;

    @Test
    void denyFirst_should_remove_allowed_when_denied() {
        var u = new com.example.EnterpriseRagCommunity.entity.access.UsersEntity();
        u.setEmail("t1@example.com");
        u.setUsername("t1");
        u.setPasswordHash("x");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        u = usersRepository.save(u);

        var roleA = new UserRolesEntity();
        roleA.setRoles("ADMIN");
        roleA.setTenantId(null);
        roleA.setCanLogin(true);
        roleA.setCanViewAnnouncement(true);
        roleA.setCanViewHelpArticles(true);
        roleA.setCanResetOwnPassword(true);
        roleA.setCanComment(true);
        roleA.setCreatedAt(LocalDateTime.now());
        roleA.setUpdatedAt(LocalDateTime.now());
        roleA = userRolesRepository.save(roleA);

        var roleB = new UserRolesEntity();
        roleB.setRoles("MODERATOR");
        roleB.setTenantId(null);
        roleB.setCanLogin(true);
        roleB.setCanViewAnnouncement(true);
        roleB.setCanViewHelpArticles(true);
        roleB.setCanResetOwnPassword(true);
        roleB.setCanComment(true);
        roleB.setCreatedAt(LocalDateTime.now());
        roleB.setUpdatedAt(LocalDateTime.now());
        roleB = userRolesRepository.save(roleB);

        var linkA = new UserRoleLinksEntity();
        linkA.setUserId(u.getId());
        linkA.setRoleId(roleA.getId());
        userRoleLinksRepository.save(linkA);

        var linkB = new UserRoleLinksEntity();
        linkB.setUserId(u.getId());
        linkB.setRoleId(roleB.getId());
        userRoleLinksRepository.save(linkB);

        PermissionsEntity p = new PermissionsEntity();
        p.setResource("permission");
        p.setAction("write");
        p.setDescription("test");
        p = permissionsRepository.save(p);

        RolePermissionsEntity allow = new RolePermissionsEntity();
        allow.setRoleId(roleA.getId());
        allow.setPermissionId(p.getId());
        allow.setAllow(true);
        rolePermissionsRepository.save(allow);

        RolePermissionsEntity deny = new RolePermissionsEntity();
        deny.setRoleId(roleB.getId());
        deny.setPermissionId(p.getId());
        deny.setAllow(false);
        rolePermissionsRepository.save(deny);

        var auths = accessControlService.buildAuthorities(u.getId());
        assertThat(auths).extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_MODERATOR")
                .doesNotContain("PERM_permission:write");

        // cleanup to reduce cross-test impact
        RolePermissionId id1 = new RolePermissionId();
        id1.setRoleId(roleA.getId());
        id1.setPermissionId(p.getId());
        rolePermissionsRepository.deleteById(id1);

        RolePermissionId id2 = new RolePermissionId();
        id2.setRoleId(roleB.getId());
        id2.setPermissionId(p.getId());
        rolePermissionsRepository.deleteById(id2);

        userRoleLinksRepository.deleteAll(userRoleLinksRepository.findByUserId(u.getId()));
        userRolesRepository.deleteById(roleA.getId());
        userRolesRepository.deleteById(roleB.getId());
        permissionsRepository.deleteById(p.getId());
        usersRepository.deleteById(u.getId());
    }
}

