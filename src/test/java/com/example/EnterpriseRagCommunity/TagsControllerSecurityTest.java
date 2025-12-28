package com.example.EnterpriseRagCommunity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TagsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tagsApi_should_require_authentication() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tagsApi_authenticated_should_pass_security() throws Exception {
        // 这里只验证安全链允许通过（不验证业务结果）；由于没有准备数据源，可能会在之后抛出 500。
        // 但至少不应该是 401。
        mockMvc.perform(get("/api/tags").with(SecurityMockMvcRequestPostProcessors.user("u").roles("ADMIN")))
                .andExpect(not401());
    }

    private static org.springframework.test.web.servlet.ResultMatcher not401() {
        return result -> {
            int s = result.getResponse().getStatus();
            if (s == 401) {
                throw new AssertionError("Expected status != 401, but was 401");
            }
        };
    }
}
