package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class RagEvalRunsCreateDTO {
    @NotBlank
    @Size(max = 96)
    @ApiModelProperty(value = "评测批次名称", required = true, example = "baseline-2025-11-06")
    private String name;

    @ApiModelProperty(value = "评测配置（JSON）")
    private Map<String, Object> config;

    @NotNull
    @ApiModelProperty(value = "是否基线", required = true, example = "false")
    private Boolean isBaseline;

    @JsonIgnore
    @ApiModelProperty(value = "创建时间（由DB默认填充）")
    private LocalDateTime createdAt;
}
