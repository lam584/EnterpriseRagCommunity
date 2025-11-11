package com.example.EnterpriseRagCommunity.dto.semantic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RetrievalEventsCreateDTO {
    @ApiModelProperty(value = "触发检索的用户ID")
    private Long userId; // SQL 允许 NULL

    @ApiModelProperty(value = "查询文本", required = true)
    @NotBlank
    private String queryText; // TEXT NOT NULL

    @ApiModelProperty(value = "BM25 TopK")
    private Integer bm25K; // NULL 允许

    @ApiModelProperty(value = "向量召回 TopK")
    private Integer vecK; // NULL 允许

    @ApiModelProperty(value = "融合后保留 TopK")
    private Integer hybridK; // NULL 允许

    @ApiModelProperty(value = "重排模型")
    @Size(max = 64)
    private String rerankModel; // VARCHAR(64) NULL

    @ApiModelProperty(value = "重排TopK")
    private Integer rerankK; // NULL 允许

    @ApiModelProperty(value = "创建时间(由数据库默认填充)")
    @JsonIgnore // 建议由 DB 默认值填充，前端可不传
    private LocalDateTime createdAt; // NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
}

