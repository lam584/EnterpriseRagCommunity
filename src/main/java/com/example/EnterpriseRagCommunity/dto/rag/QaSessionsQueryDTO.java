package com.example.EnterpriseRagCommunity.dto.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class QaSessionsQueryDTO {
    @ApiModelProperty(value = "会话ID")
    private Optional<Long> id = Optional.empty();

    @ApiModelProperty(value = "所属用户ID")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty(value = "会话标题，支持模糊/精确匹配")
    @Size(max = 191)
    private Optional<String> title = Optional.empty();

    @ApiModelProperty(value = "上下文策略，枚举：RECENT_N/SUMMARIZE/NONE")
    private Optional<ContextStrategy> contextStrategy = Optional.empty();

    @ApiModelProperty(value = "是否有效")
    private Optional<Boolean> isActive = Optional.empty();

    @ApiModelProperty(value = "创建时间开始（>=）")
    private Optional<LocalDateTime> createdAtFrom = Optional.empty();

    @ApiModelProperty(value = "创建时间结束（<=）")
    private Optional<LocalDateTime> createdAtTo = Optional.empty();

    // 单值精确查询（保留原字段，范围查询补充）
    @ApiModelProperty(value = "创建时间精确匹配")
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

