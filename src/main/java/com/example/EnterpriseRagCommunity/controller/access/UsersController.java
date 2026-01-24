package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.UsersCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.service.access.UsersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsersController {

    private final UsersService usersService;

    @PostMapping
    public ResponseEntity<UsersUpdateDTO> create(@RequestBody @Valid UsersCreateDTO dto) {
        UsersEntity entity = usersService.create(dto);
        return ResponseEntity.ok(convertToDTO(entity));
    }

    @PutMapping
    public ResponseEntity<UsersUpdateDTO> update(@RequestBody @Valid UsersUpdateDTO dto) {
        UsersEntity entity = usersService.update(dto);
        return ResponseEntity.ok(convertToDTO(entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        usersService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 永久删除（硬删）：物理删除用户记录。
     *
     * 约束：默认要求用户已先软删除（is_deleted=true），否则返回 500（RuntimeException）。
     */
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDelete(@PathVariable("id") Long id) {
        usersService.hardDelete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/query")
    public ResponseEntity<Page<UsersUpdateDTO>> query(@RequestBody UsersQueryDTO queryDTO) {
        Page<UsersEntity> page = usersService.query(queryDTO);
        return ResponseEntity.ok(page.map(this::convertToDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsersUpdateDTO> getById(@PathVariable("id") Long id) {
        UsersEntity entity = usersService.getById(id);
        return ResponseEntity.ok(convertToDTO(entity));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<Void> assignRoles(@PathVariable("id") Long id, @RequestBody List<Long> roleIds) {
        usersService.assignRoles(id, roleIds);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/roles")
    public ResponseEntity<List<RoleLinkDTO>> getUserRoles(@PathVariable("id") Long id) {
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
        return dto;
    }

    /**
     * 最小 DTO：仅用于前端分配角色（user_id, role_id）。
     */
    public static class RoleLinkDTO {
        private Long userId;
        private Long roleId;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
    }
}
