package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PostsCreateDTO {
    @ApiModelProperty(value = "所属租户ID，可选", example = "1")
    private Long tenantId;

    @NotNull
    @ApiModelProperty(value = "板块ID", required = true, example = "10")
    private Long boardId;

    @NotNull
    @ApiModelProperty(value = "作者ID", required = true, example = "200")
    private Long authorId;

    @NotBlank
    @Size(max = 191)
    @ApiModelProperty(value = "标题", required = true, example = "Site maintenance notice")
    private String title;

    @NotBlank
    @ApiModelProperty(value = "内容", required = true)
    private String content;

    @NotNull
    @ApiModelProperty(value = "内容格式", required = true, example = "MARKDOWN")
    private ContentFormat contentFormat;

    @NotNull
    @ApiModelProperty(value = "帖子状态", required = true, example = "DRAFT")
    private PostStatus status;

    @ApiModelProperty(value = "发布时间，可选", example = "2025-01-01T00:00:00")
    private LocalDateTime publishedAt;

    @JsonIgnore
    @ApiModelProperty(value = "是否删除，默认 false，由系统控制", example = "false")
    private Boolean isDeleted;

    @ApiModelProperty(value = "自定义元数据(JSON)")
    private Map<String, Object> metadata;

    @JsonIgnore
    @ApiModelProperty(value = "创建时间，由数据库默认值填充")
    private LocalDateTime createdAt;

    @JsonIgnore
    @ApiModelProperty(value = "更新时间，由数据库默认值填充")
    private LocalDateTime updatedAt;
}
