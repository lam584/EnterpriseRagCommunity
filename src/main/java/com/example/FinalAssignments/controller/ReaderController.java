package com.example.FinalAssignments.controller;

import com.example.FinalAssignments.dto.ReaderDTO;
import com.example.FinalAssignments.entity.Reader;
import com.example.FinalAssignments.entity.ReaderPermission;
import com.example.FinalAssignments.service.ReaderPermissionService;
import com.example.FinalAssignments.service.ReaderService;
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
@RequestMapping("/api/readers")
@CrossOrigin(origins = "*")
public class ReaderController {

    @Autowired
    private ReaderService readerService;

    @Autowired
    private ReaderPermissionService readerPermissionService;

    @Autowired
    private ReaderDTO.Converter readerDTOConverter;

    @GetMapping
    public ResponseEntity<List<Reader>> getReaders(
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email) {
        List<Reader> list = readerService.search(account, phone, email);
        // 确保清除所有读者的密码信息
        list.forEach(reader -> reader.setPassword(null));
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    // 返回ReaderDTO列表的API
    @GetMapping("/dto")
    public ResponseEntity<List<ReaderDTO>> getReadersDTO(
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email) {
        List<Reader> list = readerService.search(account, phone, email);
        List<ReaderDTO> dtoList = list.stream()
                .map(readerDTOConverter::convertToDTO)
                .collect(Collectors.toList());
        return new ResponseEntity<>(dtoList, HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Reader>> searchReaders(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email) {
        List<Reader> list;
        if (id != null) {
            Optional<Reader> reader = readerService.findById(id);
            list = reader.isPresent() ? List.of(reader.get()) : List.of();
        } else {
            list = readerService.search(account, phone, email);
        }
        // 确保清除所有读者的密码信息
        list.forEach(reader -> reader.setPassword(null));
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    // 搜索并返回DTO格式
    @GetMapping("/search/dto")
    public ResponseEntity<List<ReaderDTO>> searchReadersDTO(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email) {
        List<Reader> list;
        if (id != null) {
            Optional<Reader> reader = readerService.findById(id);
            list = reader.isPresent() ? List.of(reader.get()) : List.of();
        } else {
            list = readerService.search(account, phone, email);
        }

        // 转换为DTO列表
        List<ReaderDTO> dtoList = list.stream()
                .map(readerDTOConverter::convertToDTO)
                .collect(Collectors.toList());

        return new ResponseEntity<>(dtoList, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getReaderById(@PathVariable Long id) {
        Optional<Reader> reader = readerService.findById(id);
        if (reader.isPresent()) {
            // 使用DTO转换器将实体转换为DTO
            ReaderDTO dto = readerDTOConverter.convertToDTO(reader.get());
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } else {
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
    }

    // 返回ReaderDTO的通过ID获取方法
    @GetMapping("/{id}/dto")
    public ResponseEntity<?> getReaderDTOById(@PathVariable Long id) {
        Optional<Reader> reader = readerService.findById(id);
        if (reader.isPresent()) {
            ReaderDTO dto = readerDTOConverter.convertToDTO(reader.get());
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } else {
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<?> createReader(@Valid @RequestBody Reader reader) {
        try {
            // 检查必要参数
            if (reader.getPassword() == null || reader.getPassword().isEmpty()) {
                Map<String, String> res = new HashMap<>();
                res.put("message", "密码不能为空");
                return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
            }

            // 处理权限关系
            if (reader.getPermission() != null && reader.getPermission().getId() != null) {
                Long permissionId = reader.getPermission().getId();
                Optional<ReaderPermission> permissionOpt = readerPermissionService.findById(permissionId);
                if (!permissionOpt.isPresent()) {
                    Map<String, String> res = new HashMap<>();
                    res.put("message", "指定的权限ID不存在");
                    return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
                }
                // 设置完整的权限对象
                reader.setPermission(permissionOpt.get());
            }

            Reader saved = readerService.save(reader);
            // 转换为DTO返回，避免Hibernate代理序列化问题
            ReaderDTO responseDTO = readerDTOConverter.convertToDTO(saved);
            return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "创建读者失败: " + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReader(@PathVariable Long id, @Valid @RequestBody Reader readerDetails) {
        try {
            Optional<Reader> exist = readerService.findById(id);
            if (!exist.isPresent()) {
                Map<String, String> res = new HashMap<>();
                res.put("message", "读者不存在");
                return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
            }

            // 处理权限关系
            if (readerDetails.getPermission() != null && readerDetails.getPermission().getId() != null) {
                Long permissionId = readerDetails.getPermission().getId();
                Optional<ReaderPermission> permissionOpt = readerPermissionService.findById(permissionId);
                if (!permissionOpt.isPresent()) {
                    Map<String, String> res = new HashMap<>();
                    res.put("message", "指定的权限ID不存在");
                    return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
                }
                // 设置完整的权限对象
                readerDetails.setPermission(permissionOpt.get());
            }

            readerDetails.setId(id);
            Reader updated = readerService.save(readerDetails);

            // 转换为DTO返回，避免Hibernate代理序列化问题
            ReaderDTO responseDTO = readerDTOConverter.convertToDTO(updated);
            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            // 捕获特定业务异常
            Map<String, String> res = new HashMap<>();
            res.put("message", e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "数据操作失败，请联系系统管理员");
            res.put("error", e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReader(@PathVariable Long id) {
        try {
            Optional<Reader> reader = readerService.findById(id);
            if (!reader.isPresent()) {
                Map<String, String> res = new HashMap<>();
                res.put("message", "读者不存在");
                return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
            }

            readerService.delete(id);
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者删除成功");
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "删除失败: " + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

