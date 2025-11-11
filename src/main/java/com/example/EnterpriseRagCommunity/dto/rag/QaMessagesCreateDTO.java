package com.example.EnterpriseRagCommunity.dto.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaMessagesCreateDTO {
    @ApiModelProperty(value = "会话ID", required = true)
    @NotNull
    private Long sessionId;

    @ApiModelProperty(value = "消息角色", required = true, example = "USER")
    @NotNull
    private MessageRole role;

    @ApiModelProperty(value = "消息内容", required = true)
    @NotNull
    private String content;

    @ApiModelProperty(value = "生成模型")
    @Size(max = 64)
    private String model;

    @ApiModelProperty(value = "输入Token")
    private Integer tokensIn;

    @ApiModelProperty(value = "输出Token")
    private Integer tokensOut;

    @ApiModelProperty(value = "创建时间（由DB默认值填充）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

