package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.UsersCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersUpdateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UserBanActionRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.UsersService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsersController {

    private final UsersService usersService;
    private final AdministratorService administratorService;

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public ResponseEntity<UsersUpdateDTO> create(@RequestBody @Valid UsersCreateDTO dto) {
        UsersEntity entity = usersService.create(dto);
        return ResponseEntity.ok(convertToDTO(entity));
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public ResponseEntity<UsersUpdateDTO> update(@RequestBody @Valid UsersUpdateDTO dto) {
        UsersEntity actor = currentUserOrThrow();
        UsersEntity entity = usersService.update(dto, actor.getId());
        return ResponseEntity.ok(convertToDTO(entity));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        UsersEntity actor = currentUserOrThrow();
        usersService.delete(id, actor.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/ban")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public ResponseEntity<UsersUpdateDTO> ban(@PathVariable Long id, @Valid @RequestBody UserBanActionRequest req) {
        UsersEntity actor = currentUserOrThrow();
        UsersEntity updated = usersService.banUser(id, actor.getId(), actorName(actor), req.getReason(), "ADMIN_USERS", null);
        return ResponseEntity.ok(convertToDTO(updated));
    }

    @PostMapping("/{id}/unban")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public ResponseEntity<UsersUpdateDTO> unban(@PathVariable Long id, @Valid @RequestBody UserBanActionRequest req) {
        UsersEntity actor = currentUserOrThrow();
        UsersEntity updated = usersService.unbanUser(id, actor.getId(), actorName(actor), req.getReason());
        return ResponseEntity.ok(convertToDTO(updated));
    }

    /**
     * 永久删除（硬删）：物理删除用户记录。
     *
     * 约束：默认要求用户已先软删除（is_deleted=true），否则返回 500（RuntimeException）。
     */
    @DeleteMapping("/{id}/hard")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        UsersEntity actor = currentUserOrThrow();
        usersService.hardDelete(id, actor.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/query")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public ResponseEntity<Page<UsersUpdateDTO>> query(@RequestBody UsersQueryDTO queryDTO) {
        Page<UsersEntity> page = usersService.query(queryDTO);
        return ResponseEntity.ok(page.map(this::convertToDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public ResponseEntity<UsersUpdateDTO> getById(@PathVariable Long id) {
        UsersEntity entity = usersService.getById(id);
        return ResponseEntity.ok(convertToDTO(entity));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_user_roles','write'))")
    @com.example.EnterpriseRagCommunity.security.stepup.RequireAdminStepUp
    public ResponseEntity<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        usersService.assignRoles(id, roleIds);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_user_roles','read'))")
    public ResponseEntity<List<RoleLinkDTO>> getUserRoles(@PathVariable Long id) {
        List<UserRoleLinksEntity> roles = usersService.getUserRoles(id);
        return ResponseEntity.ok(roles.stream().map(this::convertToRoleLinkDTO).collect(Collectors.toList()));
    }

    private UsersUpdateDTO convertToDTO(UsersEntity entity) {
        UsersUpdateDTO dto = new UsersUpdateDTO();
        dto.setId(entity.getId());
        if (entity.getTenantId() != null) {
            dto.setTenantId(entity.getTenantId().getId());
        }
        dto.setEmail(entity.getEmail());
        dto.setUsername(entity.getUsername());
        // dto.setPasswordHash(entity.getPasswordHash()); // Do not return password hash
        dto.setStatus(entity.getStatus());
        dto.setIsDeleted(entity.getIsDeleted());
        dto.setMetadata(entity.getMetadata());

        // audit fields for admin list
        dto.setLastLoginAt(entity.getLastLoginAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        return dto;
    }

    private RoleLinkDTO convertToRoleLinkDTO(UserRoleLinksEntity entity) {
        RoleLinkDTO dto = new RoleLinkDTO();
        dto.setUserId(entity.getUserId());
        dto.setRoleId(entity.getRoleId());
        dto.setScopeType(entity.getScopeType());
        dto.setScopeId(entity.getScopeId());
        dto.setExpiresAt(entity.getExpiresAt());
        return dto;
    }

    private UsersEntity currentUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
            };
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"));
    }

    private static String actorName(UsersEntity user) {
        if (user == null) return null;
        String u = user.getUsername();
        if (u != null && !u.isBlank()) return u;
        return user.getEmail();
    }

    /**
     * 最小 DTO：仅用于前端分配角色（user_id, role_id）。
     */
    @Setter
    @Getter
    public static class RoleLinkDTO {
        private Long userId;
        private Long roleId;
        private String scopeType;
        private Long scopeId;
        private java.time.LocalDateTime expiresAt;

    }
}
