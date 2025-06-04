package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.Book;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图书数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookDTO {
    private Long id;
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String edition;
    private BigDecimal price;
    private BookCategoryDTO category; // 使用分类DTO
    private BookShelfDTO shelf; // 使用书架DTO
    private String status;
    private String printTimes;
    private AdministratorDTO administrator; // 使用管理员DTO
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 图书DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static BookDTO fromEntity(Book entity) {
            if (entity == null) {
                return null;
            }

            BookDTO dto = new BookDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换关联实体
            if (entity.getCategory() != null) {
                dto.setCategory(BookCategoryDTO.Converter.fromEntity(entity.getCategory()));
            }
            if (entity.getShelf() != null) {
                dto.setShelf(BookShelfDTO.Converter.fromEntity(entity.getShelf()));
            }
            if (entity.getAdministrator() != null) {
                dto.setAdministrator(AdministratorDTO.Converter.fromEntity(entity.getAdministrator()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static Book toEntity(BookDTO dto) {
            if (dto == null) {
                return null;
            }

            Book entity = new Book();

            entity.setIsbn(dto.getIsbn());
            entity.setTitle(dto.getTitle());
            entity.setAuthor(dto.getAuthor());
            entity.setPublisher(dto.getPublisher());
            entity.setEdition(dto.getEdition());
            entity.setPrice(dto.getPrice());
            entity.setStatus(dto.getStatus());
            entity.setPrintTimes(dto.getPrintTimes());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换关联实体（这里只设置ID，实际操作中应该从数据库获取完整实体）
            if (dto.getCategory() != null && dto.getCategory().getId() != null) {
                var category = new com.example.FinalAssignments.entity.BookCategory();
                category.setId(dto.getCategory().getId());
                entity.setCategory(category);
            }

            if (dto.getShelf() != null && dto.getShelf().getId() != null) {
                var shelf = new com.example.FinalAssignments.entity.BookShelf();
                shelf.setId(dto.getShelf().getId());
                entity.setShelf(shelf);
            }

            if (dto.getAdministrator() != null && dto.getAdministrator().getId() != null) {
                var admin = new com.example.FinalAssignments.entity.Administrator();
                admin.setId(dto.getAdministrator().getId());
                entity.setAdministrator(admin);
            }

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(BookDTO dto, Book entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getIsbn() != null) entity.setIsbn(dto.getIsbn());
            if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
            if (dto.getAuthor() != null) entity.setAuthor(dto.getAuthor());
            if (dto.getPublisher() != null) entity.setPublisher(dto.getPublisher());
            if (dto.getEdition() != null) entity.setEdition(dto.getEdition());
            if (dto.getPrice() != null) entity.setPrice(dto.getPrice());
            if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
            if (dto.getPrintTimes() != null) entity.setPrintTimes(dto.getPrintTimes());

            // 更新分类
            if (dto.getCategory() != null && dto.getCategory().getId() != null) {
                var category = new com.example.FinalAssignments.entity.BookCategory();
                category.setId(dto.getCategory().getId());
                entity.setCategory(category);
            }

            // 更新书架
            if (dto.getShelf() != null && dto.getShelf().getId() != null) {
                var shelf = new com.example.FinalAssignments.entity.BookShelf();
                shelf.setId(dto.getShelf().getId());
                entity.setShelf(shelf);
            }

            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<BookDTO> fromEntityList(List<Book> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
