package com.example.FinalAssignments.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 批量借阅请求 DTO
 */
@Data
public class BulkBookLoanRequestDTO {
    @NotNull(message = "readerId 不能为空")
    private Long readerId;

    @NotEmpty(message = "bookIds 列表不能为空")
    private List<@NotNull(message = "单个 bookId 不能为空") Long> bookIds;

    @NotNull(message = "durationDays 不能为空")
    private Integer durationDays;
}
