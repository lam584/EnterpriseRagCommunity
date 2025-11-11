package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
public class VectorIndicesQueryDTO {
    @ApiModelProperty(value = "主键ID")
    private Optional<Long> id;

    @ApiModelProperty(value = "向量引擎提供方")
    private Optional<VectorIndexProvider> provider;

    @ApiModelProperty(value = "集合名精确匹配")
    private Optional<String> collectionName;

    @ApiModelProperty(value = "集合名模糊匹配(LIKE)")
    private Optional<String> collectionNameLike;

    @ApiModelProperty(value = "距离度量精确匹配")
    private Optional<String> metric;

    @ApiModelProperty(value = "距离度量模糊匹配(LIKE)")
    private Optional<String> metricLike;

    @ApiModelProperty(value = "最小向量维度")
    private Optional<Integer> dimFrom;

    @ApiModelProperty(value = "最大向量维度")
    private Optional<Integer> dimTo;

    @ApiModelProperty(value = "状态")
    private Optional<VectorIndexStatus> status;

    @ApiModelProperty(value = "元数据键包含")
    private Optional<String> metadataContainsKey;

    @ApiModelProperty(value = "元数据值包含(字符串包含)")
    private Optional<String> metadataContainsValue;

    @ApiModelProperty(value = "原始元数据JSON")
    private Optional<Map<String, Object>> metadata;
}

