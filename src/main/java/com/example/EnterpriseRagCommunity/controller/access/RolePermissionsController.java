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

    @GetMapping("/role/{roleId}")
    @ApiOperation("查询某个角色的权限列表")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RolePermissionViewDTO>> listByRole(@PathVariable Long roleId) {
        return ResponseEntity.ok(rolePermissionsService.listByRoleId(roleId));
    }

    @PutMapping
    @ApiOperation("新增/更新 角色-权限(allow/deny)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RolePermissionViewDTO> upsert(@RequestBody @Valid RolePermissionUpsertDTO dto) {
        return ResponseEntity.ok(rolePermissionsService.upsert(dto));
    }

    @DeleteMapping
    @ApiOperation("删除 角色-权限")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@RequestParam Long roleId, @RequestParam Long permissionId) {
        rolePermissionsService.delete(roleId, permissionId);
        return ResponseEntity.ok().build();
    }
}
