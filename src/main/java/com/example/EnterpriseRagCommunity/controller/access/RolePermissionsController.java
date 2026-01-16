package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.RolePermissionUpsertDTO;
import com.example.EnterpriseRagCommunity.dto.access.RolePermissionViewDTO;
import com.example.EnterpriseRagCommunity.service.access.RolePermissionsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/role-permissions")
@RequiredArgsConstructor
@Api(tags = "角色-权限矩阵")
public class RolePermissionsController {

    private final RolePermissionsService rolePermissionsService;

    @GetMapping("/roles")
    @ApiOperation("查询所有角色ID")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<List<Long>> listRoleIds() {
        return ResponseEntity.ok(rolePermissionsService.listRoleIds());
    }

    @GetMapping("/role/{roleId}")
    @ApiOperation("查询某个角色的权限列表")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<List<RolePermissionViewDTO>> listByRole(@PathVariable Long roleId) {
        return ResponseEntity.ok(rolePermissionsService.listByRoleId(roleId));
    }

    @PostMapping("/role")
    @ApiOperation("创建新角色并写入权限矩阵（roleId 自动分配）")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<List<RolePermissionViewDTO>> createRole(
            @RequestBody List<@Valid RolePermissionUpsertDTO> dtoList) {
        return ResponseEntity.ok(rolePermissionsService.createRoleWithMatrix(dtoList));
    }

    @PutMapping
    @ApiOperation("新增/更新 角色-权限(allow/deny)")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<RolePermissionViewDTO> upsert(@RequestBody @Valid RolePermissionUpsertDTO dto) {
        return ResponseEntity.ok(rolePermissionsService.upsert(dto));
    }

    @PutMapping("/role/{roleId}")
    @ApiOperation("覆盖更新某角色的权限矩阵")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<List<RolePermissionViewDTO>> replaceAllForRole(
            @PathVariable Long roleId,
            @RequestBody List<@Valid RolePermissionUpsertDTO> dtoList) {
        return ResponseEntity.ok(rolePermissionsService.replaceAllForRole(roleId, dtoList));
    }

    @DeleteMapping
    @ApiOperation("删除 角色-权限")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<Void> delete(@RequestParam Long roleId, @RequestParam Long permissionId) {
        rolePermissionsService.delete(roleId, permissionId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/role/{roleId}")
    @ApiOperation("清空某角色的权限矩阵")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<Void> clearRole(@PathVariable Long roleId) {
        rolePermissionsService.clearRole(roleId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/role-summaries")
    @ApiOperation("查询所有角色（roleId + roleName）")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<List<RolePermissionsService.RoleInfoDTO>> listRoles() {
        return ResponseEntity.ok(rolePermissionsService.listRoles());
    }
}
