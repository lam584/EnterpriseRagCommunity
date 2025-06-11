//java/com/example/FinalAssignments/controller/ReaderController.java
package com.example.FinalAssignments.controller;

import com.example.FinalAssignments.dto.ReaderDTO;
import com.example.FinalAssignments.entity.Reader;
import com.example.FinalAssignments.entity.ReaderPermission;
import com.example.FinalAssignments.service.ReaderPermissionService;
import com.example.FinalAssignments.service.ReaderService;
import org.flywaydb.core.internal.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) LocalDateTime  startDate,
            @RequestParam(required = false) LocalDateTime  endDate) {
        List<Reader> list = readerService.searchBasic(id, account, phone, email, sex, role, startDate, endDate);
        // 确保清除所有读者的密码信息
        list.forEach(reader -> reader.setPassword(null));
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    /**
     * 模糊查询读者信息，支持 id/account/phone/email
     * 返回 DTO，避免直接暴露实体
     */
    @GetMapping("/query")
    public ResponseEntity<List<ReaderDTO>> queryReaders(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email) {

        List<Reader> list = readerService.searchBasic(
                id, account, phone, email, null, null, null, null
        );
        List<ReaderDTO> dtos = list.stream()
                .map(readerDTOConverter::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
    // 返回ReaderDTO列表的API
    @GetMapping("/dto")
    public ResponseEntity<List<ReaderDTO>> getReadersDTO(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) LocalDateTime  startDate,
            @RequestParam(required = false) LocalDateTime  endDate) {
        List<Reader> list = readerService.searchBasic(id, account, phone, email, sex, role, startDate, endDate);
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
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) LocalDateTime  startDate,
            @RequestParam(required = false) LocalDateTime  endDate) {
        List<Reader> list;
        if (id != null) {
            Optional<Reader> reader = readerService.findById(id);
            list = reader.isPresent() ? List.of(reader.get()) : List.of();
        } else {
            list = readerService.searchBasic(id, account, phone, email, sex, role, startDate, endDate);
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
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate){

        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        DateTimeFormatter slashFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        DateTimeFormatter dashFmt  = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
        try {
            if (StringUtils.hasText(startDate)) {
                try {
                    startDateTime = LocalDate.parse(startDate, slashFmt).atStartOfDay();
                } catch(DateTimeParseException ex) {
                    startDateTime = LocalDate.parse(startDate, dashFmt).atStartOfDay();
                }
            }
            // 同理处理 endDate
        } catch(DateTimeParseException ex) {
            throw new IllegalArgumentException("日期格式不正确，请使用 yyyy/MM/dd 或 yyyy-MM-dd 格式");
        }
        List<Reader> list = readerService.search(id, account, phone, email, sex, role, startDateTime, endDateTime);

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
        Optional<Reader> readerOpt = readerService.findById(id);
        if (readerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "读者不存在"));
        }

        // ① 显式判断：检查是否有借阅记录
        long loanCount = readerService.countLoansByReaderId(id);
        if (loanCount > 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "该读者存在借阅记录，无法删除"));
        }

        try {
            readerService.delete(id);
            return ResponseEntity.ok(Map.of("message", "读者删除成功"));
        } catch(DataIntegrityViolationException ex) {
            // 万一仍有外键约束
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "该读者有相关数据，无法删除"));
        } catch(Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "删除失败: " + ex.getMessage()));
        }
    }
}
