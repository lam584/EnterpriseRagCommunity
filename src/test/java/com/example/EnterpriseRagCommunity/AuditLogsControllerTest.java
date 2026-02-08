package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogsRepository auditLogsRepository;

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void list_shouldReturn200() throws Exception {
        // insert one row
        AuditLogsEntity e = new AuditLogsEntity();
        e.setTenantId(null);
        e.setActorUserId(null);
        e.setAction("TEST");
        e.setEntityType("SYSTEM");
        e.setEntityId(1L);
        e.setResult(AuditResult.SUCCESS);
        e.setDetails(Map.of("message", "hello", "traceId", "t1"));
        e.setCreatedAt(LocalDateTime.now());
        auditLogsRepository.save(e);

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void list_shouldRequireAuth() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }
}

