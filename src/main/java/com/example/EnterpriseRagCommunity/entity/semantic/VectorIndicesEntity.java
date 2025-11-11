package com.example.EnterpriseRagCommunity.entity.semantic;

import com.example.EnterpriseRagCommunity.entity.converter.JsonConverter;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "vector_indices")
public class VectorIndicesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private VectorIndexProvider provider;

    @Column(name = "collection_name", nullable = false, length = 128)
    private String collectionName;

    @Column(name = "metric", nullable = false, length = 32)
    private String metric;

    @Column(name = "dim", nullable = false)
    private Integer dim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private VectorIndexStatus status;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter.class)
    @Column(name = "metadata", columnDefinition = "json", nullable = true)
    private Map<String, Object> metadata;
}
