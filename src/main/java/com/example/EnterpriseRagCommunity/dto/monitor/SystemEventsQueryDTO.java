package com.example.EnterpriseRagCommunity.dto.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.enums.SystemEventLevel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class SystemEventsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "事件级别", example = "ERROR")
    private SystemEventLevel level;

    @ApiModelProperty(value = "分类")
    private String category;

    @ApiModelProperty(value = "消息内容(精确)")
    private String message;

    @ApiModelProperty(value = "是否存在额外JSON字段", example = "true")
    private Boolean extraExists;

    @ApiModelProperty(value = "时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "时间-止")
    private LocalDateTime createdTo;

    // 可选：JSON包含匹配（简单键值对）
    @ApiModelProperty(value = "JSON额外字段匹配(键值对)")
    private Map<String, Object> extraContains;

    public SystemEventsQueryDTO() {
        this.setOrderBy("createdAt");
        this.setSort("desc");
    }
}
