package com.example.EnterpriseRagCommunity.dto.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class QaMessagesQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "会话ID")
    private Long sessionId;

    @ApiModelProperty(value = "消息角色", example = "USER")
    private MessageRole role;

    @ApiModelProperty(value = "消息内容（全文/前缀匹配可由服务层处理）")
    private String content;

    @ApiModelProperty(value = "生成模型")
    private String model;

    @ApiModelProperty(value = "输入Token")
    private Integer tokensIn;

    @ApiModelProperty(value = "输出Token")
    private Integer tokensOut;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;

    // 范围查询支持
    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "输入Token最小值")
    private Integer tokensInMin;

    @ApiModelProperty(value = "输入Token最大值")
    private Integer tokensInMax;

    @ApiModelProperty(value = "输出Token最小值")
    private Integer tokensOutMin;

    @ApiModelProperty(value = "输出Token最大值")
    private Integer tokensOutMax;
}
