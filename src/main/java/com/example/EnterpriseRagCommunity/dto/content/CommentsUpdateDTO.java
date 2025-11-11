package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class CommentsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "评论ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Optional<Long> postId = Optional.empty();

    @ApiModelProperty(value = "父评论ID", example = "0")
    private Optional<Long> parentId = Optional.empty();

    @ApiModelProperty(value = "作者ID，不可变，保留一致性", example = "200")
    @JsonIgnore
    private Optional<Long> authorId = Optional.empty();

    @ApiModelProperty(value = "评论内容")
    private Optional<String> content = Optional.empty();

    @ApiModelProperty(value = "评论状态", example = "VISIBLE")
    private Optional<CommentStatus> status = Optional.empty();

    @ApiModelProperty(value = "软删除标记")
    private Optional<Boolean> isDeleted = Optional.empty();

    @JsonIgnore
    @ApiModelProperty(value = "创建时间，不可修改")
    private Optional<LocalDateTime> createdAt = Optional.empty();

    @JsonIgnore
    @ApiModelProperty(value = "更新时间，系统生成")
    private Optional<LocalDateTime> updatedAt = Optional.empty();
}

