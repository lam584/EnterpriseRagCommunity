package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class VectorIndicesCreateDTO {
    @ApiModelProperty(value = "向量引擎提供方", required = true, example = "FAISS")
    @NotNull
    private VectorIndexProvider provider;

    @ApiModelProperty(value = "集合名", required = true, example = "docs_collection")
    @NotBlank
    @Size(max = 128)
    private String collectionName;

    @ApiModelProperty(value = "距离度量(eg. cosine, l2)", required = true, example = "cosine")
    @NotBlank
    @Size(max = 32)
    private String metric;

    @ApiModelProperty(value = "向量维度（0 表示自动推断）", required = true, example = "1024")
    @NotNull
    @Min(0)
    private Integer dim;

    @ApiModelProperty(value = "状态", required = true, example = "READY")
    @NotNull
    private VectorIndexStatus status;

    @ApiModelProperty(value = "元数据(JSON)")
    private Map<String, Object> metadata;
}
