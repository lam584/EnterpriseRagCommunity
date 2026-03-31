package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "评论ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Long postId;

    @ApiModelProperty(value = "父评论ID", example = "0")
    private Long parentId;

    @ApiModelProperty(value = "作者ID，不可变，保留一致性", example = "200")
    @JsonIgnore
    private Long authorId;

    @ApiModelProperty(value = "评论内容")
    private String content;

    @ApiModelProperty(value = "评论状态", example = "VISIBLE")
    private CommentStatus status;

    @ApiModelProperty(value = "软删除标记")
    private Boolean isDeleted;

    @JsonIgnore
    @ApiModelProperty(value = "创建时间，不可修改")
    private LocalDateTime createdAt;

    @JsonIgnore
    @ApiModelProperty(value = "更新时间，系统生成")
    private LocalDateTime updatedAt;
}

