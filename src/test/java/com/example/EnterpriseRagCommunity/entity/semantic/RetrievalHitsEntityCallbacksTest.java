package com.example.EnterpriseRagCommunity.entity.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetrievalHitsEntityCallbacksTest {

    @Test
    void prePersistShouldFillCreatedAtWhenMissing() {
        RetrievalHitsEntity entity = new RetrievalHitsEntity();
        entity.setCreatedAt(null);

        entity.prePersist();

        assertNotNull(entity.getCreatedAt());
    }
}
