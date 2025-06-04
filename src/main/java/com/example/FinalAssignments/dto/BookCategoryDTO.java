package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.BookCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图书分类数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookCategoryDTO {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 图书分类DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static BookCategoryDTO fromEntity(BookCategory entity) {
            if (entity == null) {
                return null;
            }

            BookCategoryDTO dto = new BookCategoryDTO();
            BeanUtils.copyProperties(entity, dto);

            return dto;
        }

        /**
         * DTO转实体
         */
        public static BookCategory toEntity(BookCategoryDTO dto) {
            if (dto == null) {
                return null;
            }

            BookCategory entity = new BookCategory();
            entity.setName(dto.getName());
            entity.setDescription(dto.getDescription());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(BookCategoryDTO dto, BookCategory entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getName() != null) entity.setName(dto.getName());
            if (dto.getDescription() != null) entity.setDescription(dto.getDescription());

            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<BookCategoryDTO> fromEntityList(List<BookCategory> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
