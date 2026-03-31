package com.example.EnterpriseRagCommunity.dto.monitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class MetricsEventsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "指标名称")
    private @Size(max = 96) String name;

    @ApiModelProperty(value = "标签（JSON Map）")
    private Map<String, Object> tags;

    @ApiModelProperty(value = "数值")
    private Double value;

    @ApiModelProperty(value = "时间戳(审计，不允许修改)")
    @JsonIgnore
    private LocalDateTime ts; // 保留字段以便序列化一致
}

