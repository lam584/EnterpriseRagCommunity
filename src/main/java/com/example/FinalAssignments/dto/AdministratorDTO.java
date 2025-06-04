package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.Administrator;
import com.example.FinalAssignments.entity.AdminPermission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdministratorDTO {
    private Long id;
    private String account;
    // 不包含密码字段，提高安全性
    private String phone;
    private String email;
    private String sex;
    private LocalDateTime registeredAt;
    private AdminPermissionDTO permission; // 使用权限DTO
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 管理员DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static AdministratorDTO fromEntity(Administrator entity) {
            if (entity == null) {
                return null;
            }

            AdministratorDTO dto = new AdministratorDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换权限
            if (entity.getPermission() != null) {
                dto.setPermission(AdminPermissionDTO.Converter.fromEntity(entity.getPermission()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static Administrator toEntity(AdministratorDTO dto) {
            if (dto == null) {
                return null;
            }

            Administrator entity = new Administrator();
            // 不复制id，因为创建时id应该是自动生成的
            entity.setAccount(dto.getAccount());
            entity.setPhone(dto.getPhone());
            entity.setEmail(dto.getEmail());
            entity.setSex(dto.getSex());
            entity.setRegisteredAt(dto.getRegisteredAt());
            entity.setIsActive(dto.getIsActive());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换权限
            if (dto.getPermission() != null) {
                entity.setPermission(AdminPermissionDTO.Converter.toEntity(dto.getPermission()));
            }

            return entity;
        }

        /**
         * 更新实体
         * 用于更新操作，防止空值覆盖现有值
         */
        public static void updateEntity(AdministratorDTO dto, Administrator entity) {
            if (dto == null || entity == null) {
                return;
            }

            if (dto.getAccount() != null) entity.setAccount(dto.getAccount());
            if (dto.getPhone() != null) entity.setPhone(dto.getPhone());
            if (dto.getEmail() != null) entity.setEmail(dto.getEmail());
            if (dto.getSex() != null) entity.setSex(dto.getSex());
            if (dto.getRegisteredAt() != null) entity.setRegisteredAt(dto.getRegisteredAt());
            if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());
            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());

            // 不更新密码，密码更新应该通过专门的接口完成
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<AdministratorDTO> fromEntityList(List<Administrator> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
