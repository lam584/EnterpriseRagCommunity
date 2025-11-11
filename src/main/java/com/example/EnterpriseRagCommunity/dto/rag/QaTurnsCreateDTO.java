package com.example.EnterpriseRagCommunity.dto.rag;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaTurnsCreateDTO {
    // 不包含 id（由数据库生成）

    @ApiModelProperty(value = "会话ID", required = true)
    @NotNull
    private Long sessionId;

    @ApiModelProperty(value = "问题消息ID", required = true)
    @NotNull
    private Long questionMessageId;

    @ApiModelProperty(value = "答案消息ID（可空）")
    private Long answerMessageId;

    @ApiModelProperty(value = "问答延迟(毫秒)")
    private Integer latencyMs;

    @ApiModelProperty(value = "上下文窗口ID（可空）")
    private Long contextWindowId;

    @ApiModelProperty(value = "创建时间（审计字段，系统填充）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

