package com.example.EnterpriseRagCommunity.dto.monitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class MetricsEventsCreateDTO {
    @ApiModelProperty(value = "指标名称", required = true)
    @NotNull
    @Size(max = 96)
    private String name;

    @ApiModelProperty(value = "标签（JSON Map）")
    private Map<String, Object> tags;

    @ApiModelProperty(value = "数值", required = true)
    @NotNull
    private Double value;

    @ApiModelProperty(value = "时间戳(毫秒精度，默认当前)")
    @JsonIgnore // 由数据库默认值填充，前端可不传
    private LocalDateTime ts;
}

