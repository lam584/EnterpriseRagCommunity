package com.example.EnterpriseRagCommunity.service.access;

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
 * - ROLE_{roles} (for hasRole)
 * - PERM_{resource}:{action} (for hasAuthority)
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    public static final String PERM_PREFIX = "PERM_";

    private final UsersRepository usersRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final UserRolesRepository userRolesRepository;
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

        // roles -> ROLE_xxx (Spring's hasRole('ADMIN') checks for ROLE_ADMIN)
        for (String role : data.roleNames) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }

        // permissions -> PERM_resource:action
        for (String key : data.permissionKeys) {
            authorities.add(new SimpleGrantedAuthority(PERM_PREFIX + key));
        }

        return authorities;
    }

    private AccessData loadAccessData(Long userId) {
        Set<String> roleNames = new LinkedHashSet<>();
        Set<String> permissionKeys = new LinkedHashSet<>();

        List<UserRoleLinksEntity> links = userRoleLinksRepository.findByUserId(userId);
        if (links.isEmpty()) {
            return new AccessData(roleNames, permissionKeys);
        }

        List<Long> roleIds = links.stream().map(UserRoleLinksEntity::getRoleId).toList();
        List<UserRolesEntity> roles = userRolesRepository.findAllById(roleIds);

        for (UserRolesEntity r : roles) {
            if (r.getRoles() != null && !r.getRoles().isBlank()) {
                roleNames.add(r.getRoles().trim());
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

        return new AccessData(roleNames, permissionKeys);
    }

    public static String toPermissionKey(String resource, String action) {
        return (resource == null ? "" : resource.trim()) + ":" + (action == null ? "" : action.trim());
    }

    private record AccessData(Set<String> roleNames, Set<String> permissionKeys) {
    }
}
