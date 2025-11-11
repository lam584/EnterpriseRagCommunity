package com.example.EnterpriseRagCommunity.dto.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class QaMessagesUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "会话ID")
    private Optional<Long> sessionId = Optional.empty();

    @ApiModelProperty(value = "消息角色", example = "USER")
    private Optional<MessageRole> role = Optional.empty();

    @ApiModelProperty(value = "消息内容")
    private Optional<String> content = Optional.empty();

    @ApiModelProperty(value = "生成模型")
    private Optional<String> model = Optional.empty();

    @ApiModelProperty(value = "输入Token")
    private Optional<Integer> tokensIn = Optional.empty();

    @ApiModelProperty(value = "输出Token")
    private Optional<Integer> tokensOut = Optional.empty();

    @ApiModelProperty(value = "创建时间（不允许修改）")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

