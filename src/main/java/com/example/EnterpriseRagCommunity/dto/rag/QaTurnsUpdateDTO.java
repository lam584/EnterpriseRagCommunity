package com.example.EnterpriseRagCommunity.dto.rag;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class QaTurnsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "会话ID")
    private Optional<Long> sessionId = Optional.empty();

    @ApiModelProperty(value = "问题消息ID")
    private Optional<Long> questionMessageId = Optional.empty();

    @ApiModelProperty(value = "答案消息ID")
    private Optional<Long> answerMessageId = Optional.empty();

    @ApiModelProperty(value = "问答延迟(毫秒)")
    private Optional<Integer> latencyMs = Optional.empty();

    @ApiModelProperty(value = "上下文窗口ID")
    private Optional<Long> contextWindowId = Optional.empty();

    @ApiModelProperty(value = "创建时间（不允许修改）")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

