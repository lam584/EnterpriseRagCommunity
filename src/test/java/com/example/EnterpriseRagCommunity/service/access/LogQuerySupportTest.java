package com.example.EnterpriseRagCommunity.service.access;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogQuerySupportTest {

    @Test
    void buildPageable_should_apply_defaults_and_bounds() {
        Pageable pageable = LogQuerySupport.buildPageable(0, 999, 20, 200, Sort.unsorted());

        assertEquals(0, pageable.getPageNumber());
        assertEquals(200, pageable.getPageSize());
    }

    @Test
    void trimToNull_should_trim_non_blank_and_drop_blank() {
        assertEquals("abc", LogQuerySupport.trimToNull("  abc  "));
        assertNull(LogQuerySupport.trimToNull("   "));
        assertNull(LogQuerySupport.trimToNull(null));
    }
}
