package com.example.EnterpriseRagCommunity.dto.access;

public record AccessLogEsIndexStatusDTO(
        String indexName,
        String collectionName,
        String sinkMode,
        boolean esSinkEnabled,
        boolean consumerEnabled,
        boolean exists,
        boolean available,
        String health,
        String status,
        Long docsCount,
        String storeSize,
        String availabilityMessage
) {
}
