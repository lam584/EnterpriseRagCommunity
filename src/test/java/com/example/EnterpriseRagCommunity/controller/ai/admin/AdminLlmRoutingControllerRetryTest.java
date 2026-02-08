package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingAdminConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminLlmRoutingControllerRetryTest {

    @Test
    void updateConfigRetriesOnOptimisticLock() {
        LlmRoutingAdminConfigService llmRoutingAdminConfigService = mock(LlmRoutingAdminConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminLlmRoutingController controller = new AdminLlmRoutingController(llmRoutingAdminConfigService, administratorService);

        UsersEntity u = new UsersEntity();
        u.setId(123L);
        when(administratorService.findByUsername("u")).thenReturn(Optional.of(u));

        AdminLlmRoutingConfigDTO payload = new AdminLlmRoutingConfigDTO();
        AdminLlmRoutingConfigDTO ok = new AdminLlmRoutingConfigDTO();

        when(llmRoutingAdminConfigService.updateAdminConfig(payload, 123L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, "default"))
                .thenReturn(ok);

        Principal principal = () -> "u";
        AdminLlmRoutingConfigDTO got = controller.updateConfig(payload, principal);

        assertSame(ok, got);
        verify(llmRoutingAdminConfigService, times(2)).updateAdminConfig(payload, 123L);
    }
}

