package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationPolicyConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.security.Permissions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AdminModerationPolicyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void getConfig_shouldDeny_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/moderation/policy/config")
                        .param("contentType", "POST")
                        .with(user("u@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAndUpsert_shouldWork_withPermission() throws Exception {
        String perm = Permissions.perm("admin_review", "access");

        for (ContentType ct : new ContentType[]{ContentType.POST, ContentType.COMMENT, ContentType.PROFILE}) {
            mockMvc.perform(get("/api/admin/moderation/policy/config")
                            .param("contentType", ct.name())
                            .with(user("u@example.com").authorities(new SimpleGrantedAuthority(perm))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contentType").value(ct.name()))
                    .andExpect(jsonPath("$.policyVersion").isNotEmpty())
                    .andExpect(jsonPath("$.config").isMap());
        }

        ModerationPolicyConfigDTO payload = new ModerationPolicyConfigDTO();
        payload.setContentType(ContentType.POST);
        payload.setPolicyVersion("policy_v1");
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("thresholds", Map.of("default", Map.of("T_allow", 0.19, "T_reject", 0.81)));
        payload.setConfig(cfg);

        var res = mockMvc.perform(put("/api/admin/moderation/policy/config")
                        .with(user("u@example.com").authorities(new SimpleGrantedAuthority(perm)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();
        int status = res.getResponse().getStatus();
        String body = res.getResponse().getContentAsString();
        assertEquals(200, status, "status=" + status + ", body=" + body);

        mockMvc.perform(get("/api/admin/moderation/policy/config")
                        .param("contentType", "POST")
                        .with(user("u@example.com").authorities(new SimpleGrantedAuthority(perm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("POST"))
                .andExpect(jsonPath("$.policyVersion").value("policy_v1"))
                .andExpect(jsonPath("$.config.thresholds.default.T_allow").value(0.19))
                .andExpect(jsonPath("$.config.thresholds.default.T_reject").value(0.81));
    }
}
