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
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Dev-only RBAC debugging endpoints.
 */
@RestController
@RequestMapping("/api/debug/access")
@Profile("dev")
@RequiredArgsConstructor
public class AccessDebugController {

    private final UsersRepository usersRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final UserRolesRepository userRolesRepository;
    private final RolePermissionsRepository rolePermissionsRepository;
    private final PermissionsRepository permissionsRepository;
    private final AccessControlService accessControlService;

    @GetMapping("/snapshot")
    public ResponseEntity<?> snapshot(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(new SnapshotResponse(null, false, List.of(), null));
        }

        String email = authentication.getName();

        List<String> authAuthorities = authentication.getAuthorities().stream()
                .filter(Objects::nonNull)
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .toList();

        Optional<UsersEntity> userOpt = usersRepository.findByEmailAndIsDeletedFalse(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(new SnapshotResponse(email, authentication.isAuthenticated(), authAuthorities,
                    new DbAccessSnapshot(null, List.of(), List.of(), List.of(), List.of(), List.of())));
        }

        UsersEntity user = userOpt.get();
        Long userId = user.getId();

        List<UserRoleLinksEntity> links = userRoleLinksRepository.findByUserId(userId);
        List<Long> roleIds = links.stream().map(UserRoleLinksEntity::getRoleId).filter(Objects::nonNull).distinct().toList();

        // user_roles lookup
        List<UserRolesEntity> userRoles = roleIds.isEmpty() ? List.of() : userRolesRepository.findAllById(roleIds);
        Map<Long, String> userRolesRoleNameById = new LinkedHashMap<>();
        for (UserRolesEntity r : userRoles) {
            userRolesRoleNameById.put(r.getId(), r.getRoles());
        }

        // role_permissions lookup
        List<RolePermissionsEntity> rpsAll = new ArrayList<>();
        Set<Long> allowPermIds = new LinkedHashSet<>();
        Set<Long> denyPermIds = new LinkedHashSet<>();
        Map<Long, String> rolePermRoleNameById = new LinkedHashMap<>();

        for (Long roleId : roleIds) {
            List<RolePermissionsEntity> rps = rolePermissionsRepository.findByRoleId(roleId);
            rpsAll.addAll(rps);

            for (RolePermissionsEntity rp : rps) {
                if (rp.getRoleName() != null && !rp.getRoleName().isBlank()) {
                    rolePermRoleNameById.putIfAbsent(roleId, rp.getRoleName());
                }

                if (Boolean.FALSE.equals(rp.getAllow())) {
                    denyPermIds.add(rp.getPermissionId());
                } else {
                    allowPermIds.add(rp.getPermissionId());
                }
            }
        }

        // Apply deny-first to match production logic
        allowPermIds.removeAll(denyPermIds);

        List<PermissionsEntity> allowPerms = allowPermIds.isEmpty()
                ? List.of()
                : permissionsRepository.findAllById(allowPermIds);

        List<String> allowPermKeys = allowPerms.stream()
                .map(p -> AccessControlService.toPermissionKey(p.getResource(), p.getAction()))
                .distinct()
                .toList();

        List<String> builtAuthorities = accessControlService.buildAuthorities(userId).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        DbAccessSnapshot db = new DbAccessSnapshot(
                userId,
                roleIds,
                userRolesRoleNameById.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList(),
                rolePermRoleNameById.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList(),
                allowPermKeys,
                builtAuthorities
        );

        return ResponseEntity.ok(new SnapshotResponse(email, authentication.isAuthenticated(), authAuthorities, db));
    }

    public record SnapshotResponse(
            String email,
            boolean authenticated,
            List<String> authenticationAuthorities,
            DbAccessSnapshot db
    ) {
    }

    public record DbAccessSnapshot(
            Long userId,
            List<Long> roleIdsFromUserRoleLinks,
            List<String> roleNamesFromUserRoles,
            List<String> roleNamesFromRolePermissions,
            List<String> allowedPermissionKeys,
            List<String> builtAuthoritiesFromAccessControlService
    ) {
    }
}

