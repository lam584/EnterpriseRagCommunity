package com.example.EnterpriseRagCommunity.dto.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaSessionsUpdateDTO {
    @ApiModelProperty(value = "会话ID", required = true, example = "1000")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "所属用户ID，可空")
    private Long userId;

    @ApiModelProperty(value = "会话标题，可空，最长191字符")
    @Size(max = 191)
    private String title;

    @ApiModelProperty(value = "上下文策略，枚举：RECENT_N/SUMMARIZE/NONE")
    private ContextStrategy contextStrategy;

    @ApiModelProperty(value = "是否有效")
    private Boolean isActive;

    @ApiModelProperty(value = "创建时间，审计字段，禁止更新")
    @JsonIgnore
    private LocalDateTime createdAt;
}
