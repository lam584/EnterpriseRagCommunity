package com.example.EnterpriseRagCommunity.dto.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaSessionsCreateDTO {
    // 注意：不包含 id（由数据��生成）

    @ApiModelProperty(value = "所属用户ID，可空")
    private Long userId; // NULL 允许，不加 @NotNull

    @ApiModelProperty(value = "会话标题，可空，最长191字符")
    @Size(max = 191)
    private String title; // NULL 允许

    @ApiModelProperty(value = "上下文策略，枚举：RECENT_N/SUMMARIZE/NONE", required = true)
    @NotNull
    private ContextStrategy contextStrategy; // NOT NULL

    @ApiModelProperty(value = "是否有效", required = true)
    @NotNull
    private Boolean isActive; // NOT NULL

    @ApiModelProperty(value = "创建时间，审计字段，系统生成，前端不传")
    @JsonIgnore
    private LocalDateTime createdAt; // DB 默认值
}

