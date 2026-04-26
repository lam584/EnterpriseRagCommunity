package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.PermissionsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.PermissionsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.PermissionsUpdateDTO;
import com.example.EnterpriseRagCommunity.service.access.PermissionsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@Api(tags = "权限管理")
public class PermissionsController {

    private final PermissionsService permissionsService;

    @GetMapping
    @ApiOperation("分页查询权限")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_permissions','read'))")
    public ResponseEntity<Page<PermissionsUpdateDTO>> query(PermissionsQueryDTO queryDTO) {
        return ResponseEntity.ok(permissionsService.query(queryDTO));
    }

    @GetMapping("/{id}")
    @ApiOperation("获取权限详情")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_permissions','read'))")
    public ResponseEntity<PermissionsUpdateDTO> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(permissionsService.getById(id));
    }

    @PostMapping
    @ApiOperation("创建权限")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_permissions','write'))")
    @com.example.EnterpriseRagCommunity.security.stepup.RequireAdminStepUp
    public ResponseEntity<PermissionsUpdateDTO> create(@RequestBody @Valid PermissionsCreateDTO createDTO) {
        return ResponseEntity.ok(permissionsService.create(createDTO));
    }

    @PutMapping
    @ApiOperation("更新权限")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_permissions','write'))")
    @com.example.EnterpriseRagCommunity.security.stepup.RequireAdminStepUp
    public ResponseEntity<PermissionsUpdateDTO> update(@RequestBody @Valid PermissionsUpdateDTO updateDTO) {
        return ResponseEntity.ok(permissionsService.update(updateDTO));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除权限")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_permissions','write'))")
    @com.example.EnterpriseRagCommunity.security.stepup.RequireAdminStepUp
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        permissionsService.delete(id);
        return ResponseEntity.ok().build();
    }
}
