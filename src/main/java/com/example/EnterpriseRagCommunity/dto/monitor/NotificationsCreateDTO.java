package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class NotificationsCreateDTO {
    @ApiModelProperty(value = "接收用户ID", required = true)
    @NotNull
    private Long userId;

    @ApiModelProperty(value = "通知类型", required = true)
    @NotBlank
    @Size(max = 64)
    private String type;

    @ApiModelProperty(value = "标题", required = true)
    @NotBlank
    @Size(max = 191)
    private String title;

    @ApiModelProperty(value = "内容")
    private String content;

    @ApiModelProperty(value = "阅读时间(通常创建时为空, 系统填充)")
    @JsonIgnore
    private LocalDateTime readAt;

    @ApiModelProperty(value = "创建时间(系统默认)")
    @JsonIgnore
    private LocalDateTime createdAt;
}

