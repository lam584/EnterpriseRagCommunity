//I:\JavaWeb\FinalAssignments\src\main\java\com\example\FinalAssignments\dto\BookLoanDTO.java
package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.BookLoan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图书借阅数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookLoanDTO {
    private Long id;
    private BookDTO book; // 使用图书DTO
    private ReaderDTO reader; // 使用读者DTO
    private AdministratorDTO administrator; // 使用管理员DTO
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private BigDecimal price;
    private Integer renewCount;
    private Integer renewDuration;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 图书借阅DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static BookLoanDTO fromEntity(BookLoan entity) {
            if (entity == null) {
                return null;
            }

            BookLoanDTO dto = new BookLoanDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换关联实体
            if (entity.getBook() != null) {
                dto.setBook(BookDTO.Converter.fromEntity(entity.getBook()));
            }
            if (entity.getReader() != null) {
                ReaderDTO.Converter converter = new ReaderDTO.Converter();
                dto.setReader(converter.convertToDTO(entity.getReader()));
            }
            if (entity.getAdministrator() != null) {
                dto.setAdministrator(AdministratorDTO.Converter.fromEntity(entity.getAdministrator()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static BookLoan toEntity(BookLoanDTO dto) {
            if (dto == null) {
                return null;
            }

            BookLoan entity = new BookLoan();

            entity.setStartTime(dto.getStartTime());
            entity.setEndTime(dto.getEndTime());
            entity.setStatus(dto.getStatus());
            entity.setPrice(dto.getPrice());
            entity.setRenewCount(dto.getRenewCount() != null ? dto.getRenewCount() : 0);
            entity.setRenewDuration(dto.getRenewDuration() != null ? dto.getRenewDuration() : 0);
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换关联实体（这里只设置ID，实际操作中应该从数据库获取完整实体）
            if (dto.getBook() != null && dto.getBook().getId() != null) {
                var book = new com.example.FinalAssignments.entity.Book();
                book.setId(dto.getBook().getId());
                entity.setBook(book);
            }

            if (dto.getReader() != null && dto.getReader().getId() != null) {
                var reader = new com.example.FinalAssignments.entity.Reader();
                reader.setId(dto.getReader().getId());
                entity.setReader(reader);
            }

            if (dto.getAdministrator() != null && dto.getAdministrator().getId() != null) {
                var administrator = new com.example.FinalAssignments.entity.Administrator();
                administrator.setId(dto.getAdministrator().getId());
                entity.setAdministrator(administrator);
            }

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(BookLoanDTO dto, BookLoan entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getStartTime() != null) entity.setStartTime(dto.getStartTime());
            if (dto.getEndTime() != null) entity.setEndTime(dto.getEndTime());
            if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
            if (dto.getPrice() != null) entity.setPrice(dto.getPrice());
            if (dto.getRenewCount() != null) entity.setRenewCount(dto.getRenewCount());
            if (dto.getRenewDuration() != null) entity.setRenewDuration(dto.getRenewDuration());

            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<BookLoanDTO> fromEntityList(List<BookLoan> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
