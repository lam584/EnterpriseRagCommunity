package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AiProvidersConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAiProvidersControllerRetryTest {

    @Test
    void updateConfigRetriesOnOptimisticLock() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminAiProvidersController controller = new AdminAiProvidersController(aiProvidersConfigService, administratorService);

        UsersEntity u = new UsersEntity();
        u.setId(123L);
        when(administratorService.findByUsername("u")).thenReturn(Optional.of(u));

        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        AiProvidersConfigDTO ok = new AiProvidersConfigDTO();

        when(aiProvidersConfigService.updateAdminConfig(payload, 123L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, "default"))
                .thenReturn(ok);

        Principal principal = () -> "u";
        AiProvidersConfigDTO got = controller.updateConfig(payload, principal);

        assertSame(ok, got);
        verify(aiProvidersConfigService, times(2)).updateAdminConfig(payload, 123L);
    }
}

