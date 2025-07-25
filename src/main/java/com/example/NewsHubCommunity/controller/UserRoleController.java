package com.example.NewsHubCommunity.controller;

import com.example.NewsHubCommunity.dto.UserRoleDTOs.CreateUserRoleDTO;
import com.example.NewsHubCommunity.dto.UserRoleDTOs.UpdateUserRoleDTO;
import com.example.NewsHubCommunity.dto.UserRoleDTOs.UserRoleDTO;
import com.example.NewsHubCommunity.service.UserRoleService;
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
     * 分页查询所有角色
     */
    @GetMapping
    public ResponseEntity<Page<UserRoleDTO>> getAllUserRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<UserRoleDTO> roles = userRoleService.list(PageRequest.of(page, size));
        return ResponseEntity.ok(roles);
    }

    /**
     * 根据 ID 查询单个角色
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserRoleById(@PathVariable Long id) {
        try {
            UserRoleDTO dto = userRoleService.getById(id);
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
    public ResponseEntity<?> createUserRole(@Valid @RequestBody CreateUserRoleDTO createDto) {
        try {
            UserRoleDTO created = userRoleService.create(createDto);
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
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleDTO updateDto) {
        try {
            updateDto.setId(id);
            UserRoleDTO updated = userRoleService.update(updateDto);
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
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteUserRole(@PathVariable Long id) {
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