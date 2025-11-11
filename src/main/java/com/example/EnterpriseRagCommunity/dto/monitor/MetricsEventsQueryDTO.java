package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class MetricsEventsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "ID 精确匹配")
    private Long id;

    @ApiModelProperty(value = "指标名称(精确)")
    private String name;

    @ApiModelProperty(value = "指标名称(模糊)")
    private String nameLike;

    @ApiModelProperty(value = "标签筛选（JSON Map 或者包含键/值）")
    private Map<String, Object> tags;

    @ApiModelProperty(value = "数值下限")
    private Double valueFrom;

    @ApiModelProperty(value = "数值上限")
    private Double valueTo;

    @ApiModelProperty(value = "开始时间（含）")
    private LocalDateTime tsFrom;

    @ApiModelProperty(value = "结束时间（含）")
    private LocalDateTime tsTo;

    @ApiModelProperty(value = "聚合维度", example = "DATE")
    private String groupBy; // NONE|HOUR|DATE

    public MetricsEventsQueryDTO() {
        this.setOrderBy("ts");
        this.setSort("desc");
    }
}
