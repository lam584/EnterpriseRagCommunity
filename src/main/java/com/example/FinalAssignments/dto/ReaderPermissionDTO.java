package com.example.FinalAssignments.dto;

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
 * 读者权限数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderPermissionDTO {
    private Long id;
    private String roles;
    private Boolean canLogin;
    private Boolean canReserve;
    private Boolean canViewAnnouncement;
    private Boolean canViewHelpArticles;
    private Boolean canResetOwnPassword;
    private Boolean canBorrowReturnBooks;
    private Boolean allowEditProfile;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 读者权限DTO转换器
     * 负责在ReaderPermission实体和ReaderPermissionDTO之间进行转换
     */
    @Component
    public static class Converter {
        /**
         * 将ReaderPermission实体转换为ReaderPermissionDTO
         * @param entity ReaderPermission实体
         * @return ReaderPermissionDTO对象
         */
        public static ReaderPermissionDTO fromEntity(ReaderPermission entity) {
            if (entity == null) {
                return null;
            }

            ReaderPermissionDTO dto = new ReaderPermissionDTO();
            BeanUtils.copyProperties(entity, dto);

            return dto;
        }

        /**
         * 将ReaderPermissionDTO转换为ReaderPermission实体
         * @param dto ReaderPermissionDTO对象
         * @return ReaderPermission实体
         */
        public static ReaderPermission toEntity(ReaderPermissionDTO dto) {
            if (dto == null) {
                return null;
            }

            ReaderPermission entity = new ReaderPermission();
            entity.setRoles(dto.getRoles());
            entity.setCanLogin(dto.getCanLogin());
            entity.setCanReserve(dto.getCanReserve());
            entity.setCanViewAnnouncement(dto.getCanViewAnnouncement());
            entity.setCanViewHelpArticles(dto.getCanViewHelpArticles());
            entity.setCanResetOwnPassword(dto.getCanResetOwnPassword());
            entity.setCanBorrowReturnBooks(dto.getCanBorrowReturnBooks());
            entity.setAllowEditProfile(dto.getAllowEditProfile());
            entity.setNotes(dto.getNotes());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            return entity;
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<ReaderPermissionDTO> fromEntityList(List<ReaderPermission> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 便捷方法 - 调用Converter
     */
    public static ReaderPermissionDTO fromEntity(ReaderPermission entity) {
        return Converter.fromEntity(entity);
    }

    /**
     * 便捷方法 - 调用Converter
     */
    public ReaderPermission toEntity() {
        return Converter.toEntity(this);
    }

    /**
     * 便捷方法 - 调用Converter
     */
    public static List<ReaderPermissionDTO> fromEntityList(List<ReaderPermission> entityList) {
        return Converter.fromEntityList(entityList);
    }
}
