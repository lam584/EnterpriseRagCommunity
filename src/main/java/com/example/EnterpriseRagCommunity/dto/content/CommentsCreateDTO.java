package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentsCreateDTO {
    @NotNull
    @ApiModelProperty(value = "帖子ID", required = true, example = "1000")
    private Long postId;

    @ApiModelProperty(value = "父评论ID，可选", example = "0")
    private Long parentId;

    @NotNull
    @ApiModelProperty(value = "作者ID", required = true, example = "200")
    private Long authorId;

    @NotBlank
    @ApiModelProperty(value = "评论内容", required = true)
    private String content;

    @NotNull
    @ApiModelProperty(value = "评论状态", required = true, example = "VISIBLE")
    private CommentStatus status;

    @JsonIgnore
    @ApiModelProperty(value = "软删除标记，默认 false，由系统控制")
    private Boolean isDeleted;

    @JsonIgnore
    @ApiModelProperty(value = "创建时间，由数据库默认值填充")
    private LocalDateTime createdAt;

    @JsonIgnore
    @ApiModelProperty(value = "更新时间，由数据库默认值填充")
    private LocalDateTime updatedAt;
}

