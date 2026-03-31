package com.example.EnterpriseRagCommunity.dto.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.enums.SystemEventLevel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SystemEventsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "事件级别(可选)")
    private SystemEventLevel level;

    @ApiModelProperty(value = "分类(可选)")
    @Size(max = 64)
    private String category;

    @ApiModelProperty(value = "消息内容(可选)")
    @Size(max = 255)
    private String message;

    @ApiModelProperty(value = "额外信息(JSON)(可选)")
    private Map<String, Object> extra;

    @ApiModelProperty(value = "创建时间(不可修改)")
    @JsonIgnore
    private LocalDateTime createdAt;
}

