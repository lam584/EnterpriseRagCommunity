package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class ModerationRuleHitsQueryDTO extends PageRequestDTO {
    @ApiModelProperty(value = "命中记录ID")
    private Long id;

    @ApiModelProperty(value = "内容类型", example = "POST")
    private ContentType contentType;

    @ApiModelProperty(value = "内容ID", example = "123")
    private Long contentId;

    @ApiModelProperty(value = "命中规则ID", example = "1")
    private Long ruleId;

    @ApiModelProperty(value = "命中文本片段")
    private String snippet;

    @ApiModelProperty(value = "命中时间", example = "2025-01-01T00:00:00")
    private LocalDateTime matchedAt;

    @ApiModelProperty(value = "命中时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime matchedFrom;

    @ApiModelProperty(value = "命中时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime matchedTo;
}
