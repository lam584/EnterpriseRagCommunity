package com.example.EnterpriseRagCommunity.dto.rag;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaTurnsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "会话ID")
    private Long sessionId;

    @ApiModelProperty(value = "问题消息ID")
    private Long questionMessageId;

    @ApiModelProperty(value = "答案消息ID")
    private Long answerMessageId;

    @ApiModelProperty(value = "问答延迟(毫秒)")
    private Integer latencyMs;

    @ApiModelProperty(value = "上下文窗口ID")
    private Long contextWindowId;

    @ApiModelProperty(value = "创建时间（不允许修改）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

