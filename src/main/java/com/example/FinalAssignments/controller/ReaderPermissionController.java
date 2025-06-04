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
        List<ReaderPermission> permissions = readerPermissionService.findAll();
        List<ReaderPermissionDTO> dtoList = permissions.stream()
            .map(ReaderPermissionDTO::fromEntity)
            .collect(Collectors.toList());
        return new ResponseEntity<>(dtoList, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getReaderPermissionById(@PathVariable Long id) {
        Optional<ReaderPermission> permission = readerPermissionService.findById(id);
        if (permission.isPresent()) {
            ReaderPermissionDTO dto = ReaderPermissionDTO.fromEntity(permission.get());
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } else {
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者权限不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<?> createReaderPermission(@Valid @RequestBody ReaderPermissionDTO permissionDTO) {
        try {
            ReaderPermission entity = permissionDTO.toEntity();
            ReaderPermission saved = readerPermissionService.save(entity);
            return new ResponseEntity<>(ReaderPermissionDTO.fromEntity(saved), HttpStatus.CREATED);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "创建读者权限失败：" + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReaderPermission(@PathVariable Long id, @Valid @RequestBody ReaderPermissionDTO permissionDTO) {
        Optional<ReaderPermission> exist = readerPermissionService.findById(id);
        if (!exist.isPresent()) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者权限不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }

        ReaderPermission entity = permissionDTO.toEntity();
        entity.setId(id);
        ReaderPermission updated = readerPermissionService.save(entity);
        return new ResponseEntity<>(ReaderPermissionDTO.fromEntity(updated), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteReaderPermission(@PathVariable Long id) {
        try {
            readerPermissionService.delete(id);
            Map<String, String> res = new HashMap<>();
            res.put("message", "删除成功");
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "删除读者权限失败：可能有读者正在使用此权限");
            return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
        }
    }
}
