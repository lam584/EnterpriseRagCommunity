package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.Administrator;
import com.example.FinalAssignments.entity.AdminPermission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员数据传输对象，用于API响应
 */
@Data
public class AdminResponseDTO {
    private Long id;
    private String account;
    private String phone;
    private String email;
    private String sex;
    private LocalDateTime registeredAt;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 只包含所需的权限信息，避免序列化整个权限对象
    private Long permissionId;
    private String roles;

    // 从Administrator实体转换为DTO
    public static AdminResponseDTO fromEntity(Administrator administrator) {
        AdminResponseDTO dto = new AdminResponseDTO();
        dto.setId(administrator.getId());
        dto.setAccount(administrator.getAccount());
        dto.setPhone(administrator.getPhone());
        dto.setEmail(administrator.getEmail());
        dto.setSex(administrator.getSex());
        dto.setRegisteredAt(administrator.getRegisteredAt());
        dto.setIsActive(administrator.getIsActive());
        dto.setCreatedAt(administrator.getCreatedAt());
        dto.setUpdatedAt(administrator.getUpdatedAt());

        // 安全地获取权限信息，避免延迟加载问题
        AdminPermission permission = administrator.getPermission();
        if (permission != null) {
            dto.setPermissionId(permission.getId());
            dto.setRoles(permission.getRoles());
        }

        return dto;
    }
}
