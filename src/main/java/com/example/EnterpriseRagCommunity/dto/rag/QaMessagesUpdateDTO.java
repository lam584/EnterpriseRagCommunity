package com.example.EnterpriseRagCommunity.dto.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaMessagesUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "会话ID")
    private Long sessionId;

    @ApiModelProperty(value = "消息角色", example = "USER")
    private MessageRole role;

    @ApiModelProperty(value = "消息内容")
    private String content;

    @ApiModelProperty(value = "生成模型")
    private String model;

    @ApiModelProperty(value = "输入Token")
    private Integer tokensIn;

    @ApiModelProperty(value = "输出Token")
    private Integer tokensOut;

    @ApiModelProperty(value = "创建时间（不允许修改）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

