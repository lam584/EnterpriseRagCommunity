//java/com/example/FinalAssignments/controller/UserController.java
package com.example.NewsHubCommunity.controller;
import com.example.NewsHubCommunity.dto.UserRoleDTOs.UserRoleDTO;
import com.example.NewsHubCommunity.entity.UserRole;
import com.example.NewsHubCommunity.dto.UserDTOs;
import com.example.NewsHubCommunity.entity.User;
import com.example.NewsHubCommunity.service.UserService;
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
@RequestMapping("/api/Users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;          // 原来是 UserService

    @Autowired
    private com.example.NewsHubCommunity.service.UserRoleService userRoleService;



    @GetMapping
    public ResponseEntity<List<User>> getUsers(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) LocalDateTime  startDate,
            @RequestParam(required = false) LocalDateTime  endDate) {
        System.out.println("[调试] getUsers API 被调用，参数如下：");
        System.out.println("id: " + id);
        System.out.println("account: " + account);
        System.out.println("phone: " + phone);
        System.out.println("email: " + email);
        System.out.println("sex: " + sex);
        System.out.println("role: " + role);
        System.out.println("startDate: " + startDate);
        System.out.println("endDate: " + endDate);

        List<User> list = userService.searchBasic(id, account, phone, email, sex, role, startDate, endDate);
        list.forEach(User -> User.setPassword(null));
        System.out.println("[调试] 查询到的读者数量: " + list.size());
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    /**
     * 模糊查询读者信息，支持 id/account/phone/email
     * 返回 DTO，避免直接暴露实体
     */
    @GetMapping("/query")
    public ResponseEntity<List<UserDTOs.UserDTO>> queryUsers(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email) {
        System.out.println("[调试] queryUsers API 被调用，参数如下：");
        System.out.println("id: " + id);
        System.out.println("account: " + account);
        System.out.println("phone: " + phone);
        System.out.println("email: " + email);

        List<User> list = userService.searchBasic(
                id, account, phone, email, null, null, null, null
        );
        System.out.println("[调试] 查询到的读者数量: " + list.size());

        List<UserDTOs.UserDTO> dtos = list.stream()
                .map(UserDTOs.UserDTO::fromEntity)
                .collect(Collectors.toList());
        System.out.println("[调试] 转换后的 DTO 数量: " + dtos.size());
        return ResponseEntity.ok(dtos);
    }
    // 返回UserDTO列表的API
    @GetMapping("/dto")
    public ResponseEntity<List<UserDTOs.UserDTO>> getUsersDTO(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) LocalDateTime  startDate,
            @RequestParam(required = false) LocalDateTime  endDate) {
        System.out.println("[调试] getUsersDTO API 被调用，参数如下：");
        System.out.println("id: " + id);
        System.out.println("account: " + account);
        System.out.println("phone: " + phone);
        System.out.println("email: " + email);
        System.out.println("sex: " + sex);
        System.out.println("role: " + role);
        System.out.println("startDate: " + startDate);
        System.out.println("endDate: " + endDate);

        List<User> list = userService.searchBasic(id, account, phone, email, sex, role, startDate, endDate);
        System.out.println("[调试] 查询到的读者数量: " + list.size());

        List<UserDTOs.UserDTO> dtoList = list.stream()
                .map(UserDTOs.UserDTO::fromEntity)
                .collect(Collectors.toList());
        System.out.println("[调试] 转换后的 DTO 数量: " + dtoList.size());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) LocalDateTime  startDate,
            @RequestParam(required = false) LocalDateTime  endDate) {
        System.out.println("[调试] searchUsers API 被调用，参数如下：");
        System.out.println("id: " + id);
        System.out.println("account: " + account);
        System.out.println("phone: " + phone);
        System.out.println("email: " + email);
        System.out.println("sex: " + sex);
        System.out.println("role: " + role);
        System.out.println("startDate: " + startDate);
        System.out.println("endDate: " + endDate);

        List<User> list;
        if (id != null) {
            Optional<User> User = userService.findById(id);
            list = User.isPresent() ? List.of(User.get()) : List.of();
        } else {
            list = userService.searchBasic(id, account, phone, email, sex, role, startDate, endDate);
        }
        // 确保清除所有读者的密码信息
        list.forEach(User -> User.setPassword(null));
        System.out.println("[调试] 查询到的读者数量: " + list.size());
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    // 搜索并返回DTO格式
    @GetMapping("/search/dto")
    public ResponseEntity<List<UserDTOs.UserDTO>> searchUsersDTO(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate){

        System.out.println("[调试] searchUsersDTO API 被调用，参数如下：");
        System.out.println("id: " + id);
        System.out.println("account: " + account);
        System.out.println("phone: " + phone);
        System.out.println("email: " + email);
        System.out.println("sex: " + sex);
        System.out.println("role: " + role);
        System.out.println("startDate: " + startDate);
        System.out.println("endDate: " + endDate);

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
        List<User> list = userService.search(id, account, phone, email, sex, role, startDateTime, endDateTime);

        System.out.println("[调试] 查询到的读者数量: " + list.size());

        List<UserDTOs.UserDTO> dtoList = list.stream()
                .map(UserDTOs.UserDTO::fromEntity)
                .collect(Collectors.toList());

        System.out.println("[调试] 转换后的 DTO 数量: " + dtoList.size());
        return new ResponseEntity<>(dtoList, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        System.out.println("[调试] getUserById API 被调用，ID: " + id);

        Optional<User> User = userService.findById(id);
        if (User.isPresent()) {
            // 使用DTO转换器将实体转换为DTO
            UserDTOs.UserDTO dto = UserDTOs.UserDTO.fromEntity(User.get());
            System.out.println("[调试] 查询到读者: " + dto);
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } else {
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者不存在");
            System.out.println("[调试] 未找到对应的读者");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
    }

    // 返回UserDTO的通过ID获取方法
    @GetMapping("/{id}/dto")
    public ResponseEntity<?> getUserDTOById(@PathVariable Long id) {
        System.out.println("[调试] getUserDTOById API 被调用，ID: " + id);

        Optional<User> User = userService.findById(id);
        if (User.isPresent()) {
            UserDTOs.UserDTO dto = UserDTOs.UserDTO.fromEntity(User.get());
            System.out.println("[调试] 查询到读者: " + dto);
            return new ResponseEntity<>(dto, HttpStatus.OK);
        } else {
            Map<String, String> res = new HashMap<>();
            res.put("message", "读者不存在");
            System.out.println("[调试] 未找到对应的读者");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody User User) {
        System.out.println("[调试] createUser API 被调用，参数如下：");
        System.out.println("User: " + User);

        try {
            // 检查必要参数
            if (User.getPassword() == null || User.getPassword().isEmpty()) {
                Map<String, String> res = new HashMap<>();
                res.put("message", "密码不能为空");
                System.out.println("[调���] 密码不能为空");
                return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
            }

            // 处理角色关系
            if (User.getRole() != null && User.getRole().getId() != null) {
                Long roleId = User.getRole().getId();
                UserRoleDTO roleDto = userRoleService.getById(roleId);
                // 创建一个新的UserRole对象并设置ID
                UserRole role = new UserRole();
                role.setId(roleDto.getId());
                // 设置角色对象
                User.setRole(role);
                System.out.println("[调试] 设置角色对象ID: " + roleId);
            }

            User saved = userService.save(User);
            System.out.println("[调试] 读者创建成功: " + saved);

            // 转换为DTO返回，避免Hibernate代理序列化问题
            UserDTOs.UserDTO responseDTO = UserDTOs.UserDTO.fromEntity(saved);
            return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "创建读者失败: " + e.getMessage());
            System.out.println("[调试] 创建读者失败: " + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @Valid @RequestBody User UserDetails) {
        System.out.println("[调试] updateUser API 被调用，ID: " + id);
        System.out.println("UserDetails: " + UserDetails);

        try {
            Optional<User> exist = userService.findById(id);
            if (!exist.isPresent()) {
                Map<String, String> res = new HashMap<>();
                res.put("message", "读者不存在");
                System.out.println("[调试] 读者不存在，ID: " + id);
                return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
            }

            // 处理角色关系
            if (UserDetails.getRole() != null && UserDetails.getRole().getId() != null) {
                Long roleId = UserDetails.getRole().getId();
                UserRoleDTO roleDto = userRoleService.getById(roleId);
                // 创建一个新的UserRole对象并设置ID
                UserRole role = new UserRole();
                role.setId(roleDto.getId());
                // 设置角色对象
                UserDetails.setRole(role);
                System.out.println("[调试] 设置角色对象ID: " + roleId);
            }

            UserDetails.setId(id);
            User updated = userService.save(UserDetails);
            System.out.println("[调试] 读者更新成功: " + updated);

            // 转换为DTO返回，避免Hibernate代理序列化问题
            UserDTOs.UserDTO responseDTO = UserDTOs.UserDTO.fromEntity(updated);
            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            // 捕获特定业务异常
            Map<String, String> res = new HashMap<>();
            res.put("message", e.getMessage());
            System.out.println("[调试] 参数错误: " + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "数据操作失败，请联系系统管理员");
            res.put("error", e.getMessage());
            System.out.println("[调试] 数据操作失败: " + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        System.out.println("[调试] deleteUser API 被调用，ID: " + id);

        Optional<User> UserOpt = userService.findById(id);
        if (UserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "读者不存在"));
        }

        // ① 显式判断：检查是否有借阅记录
        long loanCount = userService.countLoansByUserId(id);
        if (loanCount > 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "该读者存在借阅记录，无法删除"));
        }

        try {
            userService.delete(id);
            System.out.println("[调试] 读者删除成功，ID: " + id);
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
