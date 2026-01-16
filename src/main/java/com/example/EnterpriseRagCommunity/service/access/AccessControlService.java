package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Loads roles and permissions for a user and builds Spring Security authorities.
 *
 * Authorities produced:
 * - ROLE_{roleName} (for hasRole/hasAuthority; roleName from user_roles.roles)
 * - ROLE_ID_{roleId} (compat/debug)
 * - PERM_{resource}:{action} (for hasAuthority)
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    public static final String PERM_PREFIX = "PERM_";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_ID_PREFIX = "ROLE_ID_";

    private final UsersRepository usersRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final RolePermissionsRepository rolePermissionsRepository;
    private final PermissionsRepository permissionsRepository;

    /**
     * If true: deny (allow=false) wins over allow.
     * If false: treat allow=false as not granted (ignore).
     */
    @Value("${security.permissions.deny-first:true}")
    private boolean denyFirst;

    @Transactional(readOnly = true)
    public UsersEntity loadActiveUserByEmail(String email) {
        return usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
    }

    /**
     * Build authorities in one transaction.
     */
    @Transactional(readOnly = true)
    public List<GrantedAuthority> buildAuthorities(Long userId) {
        AccessData data = loadAccessData(userId);

        List<GrantedAuthority> authorities = new ArrayList<>();

        // roles -> ROLE_{name} + ROLE_ID_{id}
        if (!data.roleIds.isEmpty()) {
            // 仅从 role_permissions.role_name 推断 roleName；若为空，则只发放 ROLE_ID_{id}
            Map<Long, String> roleIdToName = new HashMap<>();

            for (Long roleId : data.roleIds) {
                if (roleId == null) continue;

                List<RolePermissionsEntity> rps = rolePermissionsRepository.findByRoleId(roleId);
                for (RolePermissionsEntity rp : rps) {
                    String roleName = rp.getRoleName();
                    if (roleName != null && !roleName.isBlank()) {
                        roleIdToName.put(roleId, roleName.trim());
                        break;
                    }
                }
            }

            for (Long roleId : data.roleIds) {
                if (roleId == null) continue;

                authorities.add(new SimpleGrantedAuthority(ROLE_ID_PREFIX + roleId));

                String roleName = roleIdToName.get(roleId);
                if (roleName != null && !roleName.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleName));
                }
            }
        }

        // permissions -> PERM_resource:action
        for (String key : data.permissionKeys) {
            authorities.add(new SimpleGrantedAuthority(PERM_PREFIX + key));
        }

        return authorities;
    }

    private AccessData loadAccessData(Long userId) {
        Set<Long> roleIds = new LinkedHashSet<>();
        Set<String> permissionKeys = new LinkedHashSet<>();

        List<UserRoleLinksEntity> links = userRoleLinksRepository.findByUserId(userId);
        if (links.isEmpty()) {
            return new AccessData(roleIds, permissionKeys);
        }

        for (UserRoleLinksEntity link : links) {
            if (link.getRoleId() != null) {
                roleIds.add(link.getRoleId());
            }
        }

        // role_permissions -> allowed/denied permission ids
        Set<Long> allowIds = new LinkedHashSet<>();
        Set<Long> denyIds = new LinkedHashSet<>();

        for (Long roleId : roleIds) {
            List<RolePermissionsEntity> rps = rolePermissionsRepository.findByRoleId(roleId);
            for (RolePermissionsEntity rp : rps) {
                if (Boolean.FALSE.equals(rp.getAllow())) {
                    denyIds.add(rp.getPermissionId());
                } else {
                    allowIds.add(rp.getPermissionId());
                }
            }
        }

        if (denyFirst) {
            allowIds.removeAll(denyIds);
        }

        if (!allowIds.isEmpty()) {
            List<PermissionsEntity> perms = permissionsRepository.findAllById(allowIds);
            for (PermissionsEntity p : perms) {
                permissionKeys.add(toPermissionKey(p.getResource(), p.getAction()));
            }
        }

        return new AccessData(roleIds, permissionKeys);
    }

    public static String toPermissionKey(String resource, String action) {
        return (resource == null ? "" : resource.trim()) + ":" + (action == null ? "" : action.trim());
    }

    private record AccessData(Set<Long> roleIds, Set<String> permissionKeys) {
    }
}
