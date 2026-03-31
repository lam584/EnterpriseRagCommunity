package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

@Data
public class VectorIndicesUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "向量引擎提供方", example = "FAISS")
    private VectorIndexProvider provider;
    @JsonIgnore
    private boolean hasProvider;

    @ApiModelProperty(value = "集合名", example = "docs_collection")
    private @Size(max = 128) String collectionName;
    @JsonIgnore
    private boolean hasCollectionName;

    @ApiModelProperty(value = "距离度量(eg. cosine, l2)", example = "cosine")
    private @Size(max = 32) String metric;
    @JsonIgnore
    private boolean hasMetric;

    @ApiModelProperty(value = "向量维度（0 表示自动推断）", example = "1024")
    private @Min(0) Integer dim;
    @JsonIgnore
    private boolean hasDim;

    @ApiModelProperty(value = "状态", example = "READY")
    private VectorIndexStatus status;
    @JsonIgnore
    private boolean hasStatus;

    @ApiModelProperty(value = "元数据(JSON)")
    private Map<String, Object> metadata;
    @JsonIgnore
    private boolean hasMetadata;

    public void setProvider(VectorIndexProvider provider) {
        this.provider = provider;
        this.hasProvider = true;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
        this.hasCollectionName = true;
    }

    public void setMetric(String metric) {
        this.metric = metric;
        this.hasMetric = true;
    }

    public void setDim(Integer dim) {
        this.dim = dim;
        this.hasDim = true;
    }

    public void setStatus(VectorIndexStatus status) {
        this.status = status;
        this.hasStatus = true;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        this.hasMetadata = true;
    }
}
