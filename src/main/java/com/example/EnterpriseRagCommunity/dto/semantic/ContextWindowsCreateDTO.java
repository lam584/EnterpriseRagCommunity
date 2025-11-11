package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ContextWindowsCreateDTO {
    @ApiModelProperty(value = "检索事件ID", required = true)
    @NotNull
    private Long eventId;

    @ApiModelProperty(value = "裁剪策略", required = true, example = "TOPK")
    @NotNull
    private ContextWindowPolicy policy;

    @ApiModelProperty(value = "上下文总Token数", required = true)
    @NotNull
    private Integer totalTokens;

    @ApiModelProperty(value = "入选分片ID集合(JSON)", required = true)
    @NotNull
    private Map<String, Object> chunkIds;

    @ApiModelProperty(value = "创建时间，审计字段，DB默认填充")
    @JsonIgnore
    private LocalDateTime createdAt;
}

