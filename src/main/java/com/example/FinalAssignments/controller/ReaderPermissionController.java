package com.example.FinalAssignments.controller;

import com.example.FinalAssignments.dto.ReaderPermissionDTO;
import com.example.FinalAssignments.entity.ReaderPermission;
import com.example.FinalAssignments.service.ReaderPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reader-permissions")
@CrossOrigin(origins = "*")
public class ReaderPermissionController {

    @Autowired
    private ReaderPermissionService readerPermissionService;

    @GetMapping
    public ResponseEntity<List<ReaderPermissionDTO>> getAllReaderPermissions() {
        System.out.println("获取所有读者权限开始");
        List<ReaderPermission> permissions = readerPermissionService.findAll();
        System.out.println("获取到的权限数量: " + permissions.size());
        List<ReaderPermissionDTO> dtoList = permissions.stream()
            .map(ReaderPermissionDTO::fromEntity)
            .collect(Collectors.toList());
        System.out.println("转换为DTO完成");
        return new ResponseEntity<>(dtoList, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getReaderPermissionById(@PathVariable Long id) {
        System.out.println("获取读者权限，ID: " + id);
        Optional<ReaderPermission> permission = readerPermissionService.findById(id);
        if (permission.isPresent()) {
            System.out.println("读者权限存在，开始转换为DTO");
            ReaderPermissionDTO dto = ReaderPermissionDTO.fromEntity(permission.get());
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } else {
            System.out.println("读者权限不存在，ID: " + id);
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者权限不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<?> createReaderPermission(@Valid @RequestBody ReaderPermissionDTO permissionDTO) {
        System.out.println("创建读者权限开始");
        try {
            ReaderPermission entity = permissionDTO.toEntity();
            System.out.println("转换为实体完成");
            ReaderPermission saved = readerPermissionService.save(entity);
            System.out.println("保存实体完成，ID: " + saved.getId());
            return new ResponseEntity<>(ReaderPermissionDTO.fromEntity(saved), HttpStatus.CREATED);
        } catch (Exception e) {
            System.out.println("创建读者权限失败: " + e.getMessage());
            Map<String, String> res = new HashMap<>();
            res.put("message", "创建读者权限失败：" + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReaderPermission(@PathVariable Long id, @Valid @RequestBody ReaderPermissionDTO permissionDTO) {
        System.out.println("更新读者权限开始，ID: " + id);
        Optional<ReaderPermission> exist = readerPermissionService.findById(id);
        if (!exist.isPresent()) {
            System.out.println("读者权限不存在，ID: " + id);
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者权限不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }

        ReaderPermission entity = permissionDTO.toEntity();
        entity.setId(id);
        System.out.println("转换为实体完成，开始保存更新");
        ReaderPermission updated = readerPermissionService.save(entity);
        System.out.println("更新完成，ID: " + updated.getId());
        return new ResponseEntity<>(ReaderPermissionDTO.fromEntity(updated), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteReaderPermission(@PathVariable Long id) {
        System.out.println("删除读者权限开始，ID: " + id);
        try {
            readerPermissionService.delete(id);
            System.out.println("删除成功，ID: " + id);
            Map<String, String> res = new HashMap<>();
            res.put("message", "删除成功");
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (Exception e) {
            System.out.println("删除读者权限失败：可能有读者正在使用此权限");
            Map<String, String> res = new HashMap<>();
            res.put("message", "删除读者权限失败：可能有读者正在使用此权限");
            return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
        }
    }
}
