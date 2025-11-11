package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class NotificationsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "接收用户ID(可选)")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty(value = "通知类型(可选)")
    @Size(max = 64)
    private Optional<String> type = Optional.empty();

    @ApiModelProperty(value = "标题(可选)")
    @Size(max = 191)
    private Optional<String> title = Optional.empty();

    @ApiModelProperty(value = "内容(可选)")
    private Optional<String> content = Optional.empty();

    @ApiModelProperty(value = "阅读时间(可选)")
    private Optional<LocalDateTime> readAt = Optional.empty();

    @ApiModelProperty(value = "创建时间(只读, 不可修改)")
    @JsonIgnore
    private LocalDateTime createdAt;
}
