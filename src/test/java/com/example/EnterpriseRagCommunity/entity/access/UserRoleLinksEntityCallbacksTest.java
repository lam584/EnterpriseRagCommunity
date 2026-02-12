package com.example.EnterpriseRagCommunity.entity.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserRoleLinksEntityCallbacksTest {

    @Test
    void prePersistShouldFillDefaultsAndTimestamps() {
        UserRoleLinksEntity link = new UserRoleLinksEntity();
        link.setUserId(1L);
        link.setRoleId(2L);
        link.setScopeType(null);
        link.setScopeId(null);
        link.setCreatedAt(null);
        link.setUpdatedAt(null);

        link.prePersist();

        assertEquals("GLOBAL", link.getScopeType());
        assertEquals(0L, link.getScopeId());
        assertNotNull(link.getCreatedAt());
        assertNotNull(link.getUpdatedAt());
    }
}

