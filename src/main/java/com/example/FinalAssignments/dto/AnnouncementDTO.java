package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.Announcement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 公告数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementDTO {
    private Long id;
    private String title;
    private String content;
    private AdministratorDTO administrator; // 使用管理员DTO
    private Long viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 公告DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static AnnouncementDTO fromEntity(Announcement entity) {
            if (entity == null) {
                return null;
            }

            AnnouncementDTO dto = new AnnouncementDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换管理员
            if (entity.getAdministrator() != null) {
                dto.setAdministrator(AdministratorDTO.Converter.fromEntity(entity.getAdministrator()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static Announcement toEntity(AnnouncementDTO dto) {
            if (dto == null) {
                return null;
            }

            Announcement entity = new Announcement();
            entity.setTitle(dto.getTitle());
            entity.setContent(dto.getContent());
            entity.setViewCount(dto.getViewCount() != null ? dto.getViewCount() : 0L);
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换管理员
            if (dto.getAdministrator() != null && dto.getAdministrator().getId() != null) {
                // 这里只设置ID，实际操作中应该从数据库获取完整的管理员实体
                var admin = new com.example.FinalAssignments.entity.Administrator();
                admin.setId(dto.getAdministrator().getId());
                entity.setAdministrator(admin);
            }

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(AnnouncementDTO dto, Announcement entity) {
            if (dto == null || entity == null) {
                return;
            }

            if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
            if (dto.getContent() != null) entity.setContent(dto.getContent());
            if (dto.getViewCount() != null) entity.setViewCount(dto.getViewCount());

            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<AnnouncementDTO> fromEntityList(List<Announcement> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
