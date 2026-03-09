package com.example.EnterpriseRagCommunity.service.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditDiffBuilderBranchTest {

    @Test
    void build_should_handle_map_and_object_and_mask_sensitive_keys() {
        AuditDiffBuilder builder = new AuditDiffBuilder(new ObjectMapper());

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("username", "u1");
        before.put("password", "p1");
        before.put("token", "t1");

        Demo after = new Demo();
        after.username = "u2";
        after.password = "p2";
        after.extra = "x";

        Map<String, Object> out = builder.build(before, after);
        Map<?, ?> beforeOut = (Map<?, ?>) out.get("before");
        Map<?, ?> afterOut = (Map<?, ?>) out.get("after");

        assertEquals("***", beforeOut.get("password"));
        assertEquals("***", beforeOut.get("token"));
        assertEquals("***", afterOut.get("password"));
        assertEquals("u2", afterOut.get("username"));
        assertEquals("x", afterOut.get("extra"));
        assertTrue(out.containsKey("changes"));
    }

    @Test
    void build_should_handle_null_and_no_change_values() {
        AuditDiffBuilder builder = new AuditDiffBuilder(new ObjectMapper());
        Map<String, Object> before = Map.of("a", 1);
        Map<String, Object> after = Map.of("a", 1);
        Map<String, Object> out = builder.build(before, after);
        assertEquals(0, ((java.util.List<?>) out.get("changes")).size());
    }

    static class Demo {
        public String username;
        public String password;
        public String extra;
    }
}
