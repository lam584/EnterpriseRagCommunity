package com.example.NewsHubCommunity.controller;


import com.example.NewsHubCommunity.dto.AdminPermissionDTOs.AdminPermissionDTO;
import com.example.NewsHubCommunity.dto.AdminPermissionDTOs.CreateAdminPermissionDTO;
import com.example.NewsHubCommunity.dto.AdminPermissionDTOs.UpdateAdminPermissionDTO;
import com.example.NewsHubCommunity.service.AdminPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 管理员权限管理 REST 控制器
 * 全部接口均需登录（CSRF 保护 + Session/Cookie）
 */
@RestController
@RequestMapping("/admin-permissions")
@Validated
public class AdminPermissionController {

    private final AdminPermissionService service;

    @Autowired
    public AdminPermissionController(AdminPermissionService service) {
        this.service = service;
    }

    /**
     * 获取权限列表
     */
    @GetMapping
    public ResponseEntity<List<AdminPermissionDTO>> listAll() {
        List<AdminPermissionDTO> list = service.getAllPermissions();
        return ResponseEntity.ok(list);
    }

    /**
     * 获取单条权限
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminPermissionDTO> getOne(@PathVariable Long id) {
        AdminPermissionDTO dto = service.getPermissionById(id);
        return ResponseEntity.ok(dto);
    }

    /**
     * 创建新权限
     */
    @PostMapping
    public ResponseEntity<AdminPermissionDTO> create(
            @Valid @RequestBody CreateAdminPermissionDTO dto) {
        AdminPermissionDTO created = service.createPermission(dto);
        return ResponseEntity.status(201).body(created);
    }

    /**
     * 更新权限
     */
    @PutMapping("/{id}")
    public ResponseEntity<AdminPermissionDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAdminPermissionDTO dto) {
        AdminPermissionDTO updated = service.updatePermission(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deletePermission(id);
        return ResponseEntity.noContent().build();
    }
}