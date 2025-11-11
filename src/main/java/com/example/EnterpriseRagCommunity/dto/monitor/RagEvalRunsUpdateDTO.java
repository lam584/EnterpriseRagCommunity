package com.example.EnterpriseRagCommunity.dto.monitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Data
public class RagEvalRunsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "主键ID", required = true)
    private Long id;

    @ApiModelProperty(value = "评测批次名称(可选更新)")
    private Optional<String> name = Optional.empty();

    @ApiModelProperty(value = "评测配置（JSON）(可选更新)")
    private Optional<Map<String, Object>> config = Optional.empty();

    @ApiModelProperty(value = "是否基线(可选更新)")
    private Optional<Boolean> isBaseline = Optional.empty();

    @JsonIgnore
    @ApiModelProperty(value = "创建时间（只读，不允许更新）")
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

