package com.example.EnterpriseRagCommunity.dto.rag;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class QaTurnsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "会话ID")
    private Long sessionId;

    @ApiModelProperty(value = "问题消息ID")
    private Long questionMessageId;

    @ApiModelProperty(value = "答案消息ID")
    private Long answerMessageId;

    @ApiModelProperty(value = "上下文窗口ID")
    private Long contextWindowId;

    @ApiModelProperty(value = "问答延迟(毫秒)")
    private Integer latencyMs;

    // 范围查询支持（延迟）
    @ApiModelProperty(value = "延迟下限(毫秒)")
    private Integer latencyMsFrom;

    @ApiModelProperty(value = "延迟上限(毫秒)")
    private Integer latencyMsTo;

    // 创建时间（单值 + 范围）
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;
}
