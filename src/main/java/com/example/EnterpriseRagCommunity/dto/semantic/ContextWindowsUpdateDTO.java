package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Data
public class ContextWindowsUpdateDTO {
    @ApiModelProperty(value = "上下文窗口ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "检索事件ID")
    private Optional<Long> eventId = Optional.empty();

    @ApiModelProperty(value = "裁剪策略")
    private Optional<ContextWindowPolicy> policy = Optional.empty();

    @ApiModelProperty(value = "上下文总Token数")
    private Optional<Integer> totalTokens = Optional.empty();

    @ApiModelProperty(value = "入选分片ID集合(JSON)")
    private Optional<Map<String, Object>> chunkIds = Optional.empty();

    @ApiModelProperty(value = "创建时间，审计字段，禁止更新")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

