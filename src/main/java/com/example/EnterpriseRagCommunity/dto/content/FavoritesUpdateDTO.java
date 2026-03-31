package com.example.EnterpriseRagCommunity.dto.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoritesUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "用户ID", example = "200")
    private Long userId;

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Long postId;

    @ApiModelProperty(value = "收藏时间（不允许修改）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

