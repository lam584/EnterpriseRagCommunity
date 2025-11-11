package com.example.EnterpriseRagCommunity.dto.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoritesCreateDTO {
    // id 由数据库生成，不在 CreateDTO 中出现

    @ApiModelProperty(value = "用户ID", required = true, example = "200")
    @NotNull
    private Long userId;

    @ApiModelProperty(value = "帖子ID", required = true, example = "1000")
    @NotNull
    private Long postId;

    @ApiModelProperty(value = "收藏时间（由DB默认填充）", example = "2025-01-01T00:00:00")
    @JsonIgnore
    @NotNull
    private LocalDateTime createdAt;
}

