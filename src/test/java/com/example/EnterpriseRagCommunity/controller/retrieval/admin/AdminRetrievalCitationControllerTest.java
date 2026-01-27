package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.repository.monitor.AppSettingsRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRetrievalCitationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppSettingsRepository appSettingsRepository;

    @Test
    void updateConfig_should_accept_long_json_value() throws Exception {
        appSettingsRepository.deleteById(CitationConfigService.KEY_CONFIG_JSON);

        String longInstruction = "a".repeat(1200);
        String payload = """
                {
                  "enabled": true,
                  "citationMode": "BOTH",
                  "instructionTemplate": "%s",
                  "sourcesTitle": "来源",
                  "maxSources": 6,
                  "includeUrl": true,
                  "includeScore": false,
                  "includeTitle": true,
                  "includePostId": false,
                  "includeChunkIndex": false,
                  "postUrlTemplate": "/portal/posts/detail/{postId}"
                }
                """.formatted(longInstruction);

        mockMvc.perform(put("/api/admin/retrieval/citation/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(user("admin@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_citation:write")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citationMode").value("BOTH"));

        String saved = appSettingsRepository.findById(CitationConfigService.KEY_CONFIG_JSON)
                .orElseThrow()
                .getV();
        assertThat(saved.length()).isGreaterThan(255);
    }
}

