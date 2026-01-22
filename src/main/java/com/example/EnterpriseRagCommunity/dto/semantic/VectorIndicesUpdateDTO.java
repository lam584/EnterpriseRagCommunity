package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
public class VectorIndicesUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "向量引擎提供方", example = "FAISS")
    private Optional<VectorIndexProvider> provider;

    @ApiModelProperty(value = "集合名", example = "docs_collection")
    @Size(max = 128)
    private Optional<String> collectionName;

    @ApiModelProperty(value = "距离度量(eg. cosine, l2)", example = "cosine")
    @Size(max = 32)
    private Optional<String> metric;

    @ApiModelProperty(value = "向量维度（0 表示自动推断）", example = "1024")
    @Min(0)
    private Optional<Integer> dim;

    @ApiModelProperty(value = "状态", example = "READY")
    private Optional<VectorIndexStatus> status;

    @ApiModelProperty(value = "元数据(JSON)")
    private Optional<Map<String, Object>> metadata;
}
