package com.example.EnterpriseRagCommunity.dto.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaSessionsQueryDTO {
    @ApiModelProperty(value = "会话ID")
    private Long id;

    @ApiModelProperty(value = "所属用户ID")
    private Long userId;

    @ApiModelProperty(value = "会话标题，支持模糊/精确匹配")
    @Size(max = 191)
    private String title;

    @ApiModelProperty(value = "上下文策略，枚举：RECENT_N/SUMMARIZE/NONE")
    private ContextStrategy contextStrategy;

    @ApiModelProperty(value = "是否有效")
    private Boolean isActive;

    @ApiModelProperty(value = "创建时间开始（>=）")
    private LocalDateTime createdAtFrom;

    @ApiModelProperty(value = "创建时间结束（<=）")
    private LocalDateTime createdAtTo;

    // 单值精确查询（保留原字段，范围查询补充）
    @ApiModelProperty(value = "创建时间精确匹配")
    private LocalDateTime createdAt;
}

