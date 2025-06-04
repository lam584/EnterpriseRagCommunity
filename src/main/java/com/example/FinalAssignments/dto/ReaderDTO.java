//java/com/example/FinalAssignments/dto/ReaderDTO.java
package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.Reader;
import com.example.FinalAssignments.entity.ReaderPermission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 读者数据传输对象
 * 包含读者信息及其转换器
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderDTO {
    private Long id;
    private String account;
    // 仅用于接收密码更新，不会从实体中获取
    private String password;
    private String phone;
    private String email;
    private String sex;
    private ReaderPermissionDTO permission; // 使用读者权限DTO
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 读者DTO转换器
     * 负责在Reader实体和ReaderDTO之间进行转换
     */
    @Component
    public static class Converter {
        /**
         * 将Reader实体转换为ReaderDTO
         * @param entity Reader实体
         * @return ReaderDTO对象
         */
        public ReaderDTO convertToDTO(Reader entity) {
            if (entity == null) {
                return null;
            }

            ReaderDTO dto = new ReaderDTO();
            BeanUtils.copyProperties(entity, dto, "password"); // 排除密码字段，提高安全性

            // 处理权限关系
            if (entity.getPermission() != null) {
                dto.setPermission(ReaderPermissionDTO.fromEntity(entity.getPermission()));
            }

            return dto;
        }

        /**
         * 将ReaderDTO更新到现有实体实例
         * @param dto ReaderDTO对象
         * @param entity 现有Reader实体
         */
        public void updateEntityInstance(ReaderDTO dto, Reader entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 复制非null属性
            if (dto.getAccount() != null) entity.setAccount(dto.getAccount());
            if (dto.getPhone() != null) entity.setPhone(dto.getPhone());
            if (dto.getEmail() != null) entity.setEmail(dto.getEmail());
            if (dto.getSex() != null) entity.setSex(dto.getSex());
            if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());

            // 不复制密码字段，密码更新需要单独处理
            // 不复制权限字段，权限关系需要单独处理
        }

        /**
         * 将ReaderDTO转换为新的Reader实体
         * @param dto ReaderDTO对象
         * @return 新的Reader实体
         */
        public Reader convertToEntity(ReaderDTO dto) {
            if (dto == null) {
                return null;
            }

            Reader entity = new Reader();
            BeanUtils.copyProperties(dto, entity, "permission", "createdAt", "updatedAt");

            // 不直接复制权限字段，权限关系需要单独处理

            // 设置时间
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            return entity;
        }

        /**
         * 转换DTO列表到实体列表
         */
        public List<Reader> convertToEntityList(List<ReaderDTO> dtoList) {
            if (dtoList == null) {
                return null;
            }
            return dtoList.stream()
                    .map(this::convertToEntity)
                    .collect(Collectors.toList());
        }

        /**
         * 转换实体列表到DTO列表
         */
        public List<ReaderDTO> convertToDTOList(List<Reader> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }
    }
}
