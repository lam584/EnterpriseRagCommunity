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
 * 管理员数据传输对象(Data Transfer Object)
 * 
 * <p>用于在系统各层之间传输管理员数据，不包含敏感信息如密码字段</p>
 * 
 * <p>注意事项：</p>
 * <ul>
 *   <li>不包含密码字段，确保安全性</li>
 *   <li>包含权限信息的DTO对象</li>
 *   <li>所有时间字段使用LocalDateTime类型</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdministratorDTO {
    /**
     * 管理员唯一标识ID
     */
    private Long id;
    
    /**
     * 管理员账号，用于登录系统
     */
    private String account;
    
    /**
     * 管理员联系电话
     */
    private String phone;
    
    /**
     * 管理员电子邮箱
     */
    private String email;
    
    /**
     * 管理员性别
     */
    private String sex;
    
    /**
     * 管理员注册时间
     */
    private LocalDateTime registeredAt;
    
    /**
     * 管理员权限信息DTO
     */
    private AdminPermissionDTO permission;
    
    /**
     * 账号是否激活
     */
    private Boolean isActive;
    
    /**
     * 记录创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 记录最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 管理员DTO转换器
     * 
     * <p>提供管理员实体与DTO之间的转换方法</p>
     * 
     * <p>转换规则：</p>
     * <ul>
     *   <li>自动复制同名属性</li>
     *   <li>手动处理特殊字段(如权限)</li>
     *   <li>处理空值情况</li>
     * </ul>
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
