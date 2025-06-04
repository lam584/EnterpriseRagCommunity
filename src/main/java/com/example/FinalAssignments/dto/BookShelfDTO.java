package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.BookShelf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 书架数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookShelfDTO {
    private Long id;
    private String shelfCode;
    private String locationDescription;
    private Integer capacity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 书架DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static BookShelfDTO fromEntity(BookShelf entity) {
            if (entity == null) {
                return null;
            }

            BookShelfDTO dto = new BookShelfDTO();
            BeanUtils.copyProperties(entity, dto);

            return dto;
        }

        /**
         * DTO转实体
         */
        public static BookShelf toEntity(BookShelfDTO dto) {
            if (dto == null) {
                return null;
            }

            BookShelf entity = new BookShelf();
            entity.setShelfCode(dto.getShelfCode());
            entity.setLocationDescription(dto.getLocationDescription());
            entity.setCapacity(dto.getCapacity());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(BookShelfDTO dto, BookShelf entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getShelfCode() != null) entity.setShelfCode(dto.getShelfCode());
            if (dto.getLocationDescription() != null) entity.setLocationDescription(dto.getLocationDescription());
            if (dto.getCapacity() != null) entity.setCapacity(dto.getCapacity());

            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<BookShelfDTO> fromEntityList(List<BookShelf> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
