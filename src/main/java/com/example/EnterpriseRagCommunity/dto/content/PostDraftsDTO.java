package com.example.EnterpriseRagCommunity.dto.content;

import java.time.LocalDateTime;
import java.util.Map;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class PostDraftsDTO {

    @ApiModelProperty(value = "草稿ID")
    private Long id;

    @ApiModelProperty(value = "租户ID")
    private Long tenantId;

    @ApiModelProperty(value = "板块ID")
    private Long boardId;

    @ApiModelProperty(value = "作者ID")
    private Long authorId;

    @ApiModelProperty(value = "标题")
    private String title;

    @ApiModelProperty(value = "内容")
    private String content;

    @ApiModelProperty(value = "内容格式")
    private ContentFormat contentFormat;

    @ApiModelProperty(value = "元数据(JSON)，可存 tags/attachmentIds")
    private Map<String, Object> metadata;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedAt;
}