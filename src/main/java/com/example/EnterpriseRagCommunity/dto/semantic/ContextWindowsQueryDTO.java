package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class ContextWindowsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "窗口ID")
    private Long id;

    @ApiModelProperty(value = "事件ID", example = "1000")
    private Long eventId;

    @ApiModelProperty(value = "窗口策略", example = "TOPK")
    private ContextWindowPolicy policy;

    @ApiModelProperty(value = "总token数(精确匹配)")
    private Integer totalTokens;

    @ApiModelProperty(value = "最小总token数")
    private Integer minTotalTokens;

    @ApiModelProperty(value = "最大总token数")
    private Integer maxTotalTokens;

    @ApiModelProperty(value = "分片ID集合(JSON)")
    private Map<String, Object> chunkIds;

    @ApiModelProperty(value = "创建时间(精确匹配)")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;
}
