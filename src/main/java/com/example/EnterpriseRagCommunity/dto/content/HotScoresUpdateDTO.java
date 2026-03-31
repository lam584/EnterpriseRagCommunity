package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotScoresUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "帖子ID(主键)", required = true, example = "100")
    private Long postId;

    @ApiModelProperty(value = "24小时热度分", example = "12.5")
    private Double score24h;

    @ApiModelProperty(value = "7天热度分", example = "120.3")
    private Double score7d;

    @ApiModelProperty(value = "累计热度分", example = "542.9")
    private Double scoreAll;

    @ApiModelProperty(value = "衰减基数", example = "0.85")
    private Double decayBase;

    // 审计类 / 系统计算字段：这里仍显式映射，更新时业务可限制只读
    @ApiModelProperty(value = "最后重算时间(只读或系统更新)", example = "2025-01-01T00:00:00")
    private LocalDateTime lastRecalculatedAt;
}

