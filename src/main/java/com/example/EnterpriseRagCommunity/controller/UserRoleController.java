package com.example.EnterpriseRagCommunity.controller;


import com.example.EnterpriseRagCommunity.dto.access.UserRolesCreateDTO; // 替代旧UserRoleDTO
import com.example.EnterpriseRagCommunity.service.UserRoleService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户角色管理 Controller
 */
@RestController
@RequestMapping("/api/user-roles")
public class UserRoleController {

    private final UserRoleService userRoleService;

    @Autowired
    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    /**
     * 查询所有角色（不分页）
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllUserRolesNoPage() {
        return ResponseEntity.ok(userRoleService.listAll());
    }

    /**
     * 分页查询所有角色
     */
    @GetMapping
    public ResponseEntity<Page<UserRolesCreateDTO>> getAllUserRoles( // 对齐: 返回类型改为UserRolesCreateDTO
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Page<UserRolesCreateDTO> roles = userRoleService.list(PageRequest.of(page, size)); // 对齐: UserRoleDTO → UserRolesCreateDTO
        return ResponseEntity.ok(roles);
    }

    /**
     * 根据 ID 查询单个角色
     */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<?> getUserRoleById(@PathVariable("id") Long id) {
        try {
            UserRolesCreateDTO dto = userRoleService.getById(id); // 对齐: UserRoleDTO → UserRolesCreateDTO
            return ResponseEntity.ok(dto);
        } catch (EntityNotFoundException e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "用户角色不存在");
            return ResponseEntity.status(404).body(res);
        }
    }

    /**
     * 创建新角色
     */
    @PostMapping
    public ResponseEntity<?> createUserRole(@Valid @RequestBody UserRolesCreateDTO createDto) { // 对齐: 参数类型改为UserRolesCreateDTO
        try {
            UserRolesCreateDTO created = userRoleService.create(createDto); // 对齐: UserRoleDTO → UserRolesCreateDTO
            return ResponseEntity.status(201).body(created);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "创建用户角色失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    /**
     * 更新已有角色
     */
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<?> updateUserRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserRolesCreateDTO updateDto) { // 对齐: 参数类型改为UserRolesCreateDTO
        try {
            UserRolesCreateDTO updated = userRoleService.update(id, updateDto); // 修改调用，传递 id 和 dto
            return ResponseEntity.ok(updated);
        } catch (EntityNotFoundException e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "用户角色不存在");
            return ResponseEntity.status(404).body(res);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "更新用户角色失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, String>> deleteUserRole(@PathVariable("id") Long id) {
        Map<String, String> res = new HashMap<>();
        try {
            userRoleService.delete(id);
            res.put("message", "删除成功");
            return ResponseEntity.ok(res);
        } catch (EntityNotFoundException e) {
            res.put("message", "用户角色不存在");
            return ResponseEntity.status(404).body(res);
        } catch (Exception e) {
            res.put("message", "删除用户角色失败：可能有其他记录在使用");
            return ResponseEntity.badRequest().body(res);
        }
    }
}
