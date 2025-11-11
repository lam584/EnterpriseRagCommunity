package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotScoresCreateDTO {
    // 业务若不允许前端指定 postId，可在控制器中忽略该字段；规范要求显式映射除主键外所有列，这里缓存表以 postId 作为主键，需讨论是否允许前端传入。
    @NotNull
    @ApiModelProperty(value = "帖子ID(与主键相同)", required = true, example = "100")
    private Long postId;

    @NotNull
    @ApiModelProperty(value = "24小时热度分", required = true, example = "12.5")
    private Double score24h;

    @NotNull
    @ApiModelProperty(value = "7天热度分", required = true, example = "120.3")
    private Double score7d;

    @NotNull
    @ApiModelProperty(value = "累计热度分", required = true, example = "542.9")
    private Double scoreAll;

    @NotNull
    @ApiModelProperty(value = "衰减基数", required = true, example = "0.85")
    private Double decayBase;

    @NotNull
    @ApiModelProperty(value = "最后重算时间", required = true, example = "2025-01-01T00:00:00")
    private LocalDateTime lastRecalculatedAt;
}

