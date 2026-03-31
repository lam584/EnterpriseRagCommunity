package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ContextWindowsUpdateDTO {
    @ApiModelProperty(value = "上下文窗口ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "检索事件ID")
    private Long eventId;

    @ApiModelProperty(value = "裁剪策略")
    private ContextWindowPolicy policy;

    @ApiModelProperty(value = "上下文总Token数")
    private Integer totalTokens;

    @ApiModelProperty(value = "入选分片ID集合(JSON)")
    private Map<String, Object> chunkIds;

    @ApiModelProperty(value = "创建时间，审计字段，禁止更新")
    @JsonIgnore
    private LocalDateTime createdAt;
}

