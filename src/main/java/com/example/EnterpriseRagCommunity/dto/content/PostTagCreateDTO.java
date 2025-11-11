package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostTagSource;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PostTagCreateDTO {
    @NotNull
    @ApiModelProperty(value = "帖子ID", required = true, example = "1000")
    private Long postId;

    @NotNull
    @ApiModelProperty(value = "标签ID", required = true, example = "10")
    private Long tagId;

    @NotNull
    @ApiModelProperty(value = "来源", required = true, example = "MANUAL")
    private PostTagSource source;

    @ApiModelProperty(value = "置信度", example = "0.7500")
    private BigDecimal confidence;

    @JsonIgnore
    @ApiModelProperty(value = "创建时间，数据库默认", example = "2025-11-09T00:00:00")
    private LocalDateTime createdAt;
}

