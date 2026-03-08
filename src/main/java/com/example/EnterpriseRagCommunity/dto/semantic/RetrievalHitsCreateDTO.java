package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RetrievalHitsCreateDTO {
    @ApiModelProperty(value = "检索事件ID", required = true)
    @NotNull
    private Long eventId; // event_id BIGINT NOT NULL

    @ApiModelProperty(value = "排名(从1开始)", required = true, example = "1")
    @NotNull
    @Min(1)
    private Integer rank; // rank INT NOT NULL

    @ApiModelProperty(value = "命中类型(BM25/VEC/RERANK)", required = true, example = "BM25")
    @NotNull
    private RetrievalHitType hitType; // hit_type ENUM NOT NULL

    @ApiModelProperty(value = "帖子ID，可为空")
    private Long postId; // post_id BIGINT NULL

    @ApiModelProperty(value = "分片ID，可为空")
    private Long chunkId; // chunk_id BIGINT NULL

    @ApiModelProperty(value = "得分", required = true, example = "0.876")
    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private Double score; // score DOUBLE NOT NULL
}

