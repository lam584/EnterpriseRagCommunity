package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class NotificationsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "接收用户ID(可选)")
    private Long userId;

    @ApiModelProperty(value = "通知类型(可选)")
    @Size(max = 64)
    private String type;

    @ApiModelProperty(value = "标题(可选)")
    @Size(max = 191)
    private String title;

    @ApiModelProperty(value = "内容(可选)")
    private String content;

    @ApiModelProperty(value = "阅读时间(可选)")
    private LocalDateTime readAt;

    @ApiModelProperty(value = "创建时间(只读, 不可修改)")
    @JsonIgnore
    private LocalDateTime createdAt;
}
