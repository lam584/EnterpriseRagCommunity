package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.PostLangLabelGenConfigService;
import com.example.EnterpriseRagCommunity.service.ai.PostTagGenConfigService;
import com.example.EnterpriseRagCommunity.service.ai.PostTitleGenConfigService;
import com.example.EnterpriseRagCommunity.service.ai.SemanticTranslateConfigService;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSemanticConfigControllersUnitTest {

    @Test
    void langLabelUpsertConfig_shouldPassNullActor_whenPrincipalIsNull() {
        PostLangLabelGenConfigService service = mock(PostLangLabelGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPostLangLabelGenController controller = new AdminPostLangLabelGenController(service, administratorService);

        PostLangLabelGenConfigDTO payload = new PostLangLabelGenConfigDTO();
        PostLangLabelGenConfigDTO result = new PostLangLabelGenConfigDTO();
        when(service.upsertAdminConfig(eq(payload), eq(null), eq(null))).thenReturn(result);

        controller.upsertConfig(payload, null);

        verify(service).upsertAdminConfig(eq(payload), eq(null), eq(null));
    }

    @Test
    void langLabelUpsertConfig_shouldPassResolvedActor_whenPrincipalHasExistingUser() {
        PostLangLabelGenConfigService service = mock(PostLangLabelGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPostLangLabelGenController controller = new AdminPostLangLabelGenController(service, administratorService);

        Principal principal = () -> "admin";
        UsersEntity user = new UsersEntity();
        user.setId(11L);
        when(administratorService.findByUsername("admin")).thenReturn(Optional.of(user));

        PostLangLabelGenConfigDTO payload = new PostLangLabelGenConfigDTO();
        PostLangLabelGenConfigDTO result = new PostLangLabelGenConfigDTO();
        when(service.upsertAdminConfig(eq(payload), eq(11L), eq("admin"))).thenReturn(result);

        controller.upsertConfig(payload, principal);

        verify(administratorService).findByUsername("admin");
        verify(service).upsertAdminConfig(eq(payload), eq(11L), eq("admin"));
    }

    @Test
    void langLabelUpsertConfig_shouldPassNullUserId_whenPrincipalUserMissing() {
        PostLangLabelGenConfigService service = mock(PostLangLabelGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPostLangLabelGenController controller = new AdminPostLangLabelGenController(service, administratorService);

        Principal principal = () -> "admin";
        when(administratorService.findByUsername("admin")).thenReturn(Optional.empty());

        PostLangLabelGenConfigDTO payload = new PostLangLabelGenConfigDTO();
        PostLangLabelGenConfigDTO result = new PostLangLabelGenConfigDTO();
        when(service.upsertAdminConfig(eq(payload), eq(null), eq("admin"))).thenReturn(result);

        controller.upsertConfig(payload, principal);

        verify(administratorService).findByUsername("admin");
        verify(service).upsertAdminConfig(eq(payload), eq(null), eq("admin"));
    }

    @Test
    void tagGenUpsertConfig_shouldCoverPrincipalBranches() {
        PostTagGenConfigService service = mock(PostTagGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPostTagGenController controller = new AdminPostTagGenController(service, administratorService);

        PostTagGenConfigDTO payload = new PostTagGenConfigDTO();
        PostTagGenConfigDTO result = new PostTagGenConfigDTO();
        when(service.upsertAdminConfig(eq(payload), eq(null), eq(null))).thenReturn(result);
        controller.upsertConfig(payload, null);
        verify(service).upsertAdminConfig(eq(payload), eq(null), eq(null));

        Principal principal = () -> "admin";
        UsersEntity user = new UsersEntity();
        user.setId(12L);
        when(administratorService.findByUsername("admin")).thenReturn(Optional.of(user)).thenReturn(Optional.empty());
        when(service.upsertAdminConfig(eq(payload), eq(12L), eq("admin"))).thenReturn(result);
        when(service.upsertAdminConfig(eq(payload), eq(null), eq("admin"))).thenReturn(result);

        controller.upsertConfig(payload, principal);
        controller.upsertConfig(payload, principal);

        verify(service).upsertAdminConfig(eq(payload), eq(12L), eq("admin"));
        verify(service).upsertAdminConfig(eq(payload), eq(null), eq("admin"));
    }

    @Test
    void titleGenUpsertConfig_shouldCoverPrincipalBranches() {
        PostTitleGenConfigService service = mock(PostTitleGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPostTitleGenController controller = new AdminPostTitleGenController(service, administratorService);

        PostTitleGenConfigDTO payload = new PostTitleGenConfigDTO();
        PostTitleGenConfigDTO result = new PostTitleGenConfigDTO();
        when(service.upsertAdminConfig(eq(payload), eq(null), eq(null))).thenReturn(result);
        controller.upsertConfig(payload, null);
        verify(service).upsertAdminConfig(eq(payload), eq(null), eq(null));

        Principal principal = () -> "admin";
        UsersEntity user = new UsersEntity();
        user.setId(13L);
        when(administratorService.findByUsername("admin")).thenReturn(Optional.of(user)).thenReturn(Optional.empty());
        when(service.upsertAdminConfig(eq(payload), eq(13L), eq("admin"))).thenReturn(result);
        when(service.upsertAdminConfig(eq(payload), eq(null), eq("admin"))).thenReturn(result);

        controller.upsertConfig(payload, principal);
        controller.upsertConfig(payload, principal);

        verify(service).upsertAdminConfig(eq(payload), eq(13L), eq("admin"));
        verify(service).upsertAdminConfig(eq(payload), eq(null), eq("admin"));
    }

    @Test
    void translateUpsertConfig_shouldCoverPrincipalBranches() {
        SemanticTranslateConfigService service = mock(SemanticTranslateConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminSemanticTranslateController controller = new AdminSemanticTranslateController(service, administratorService);

        SemanticTranslateConfigDTO payload = new SemanticTranslateConfigDTO();
        SemanticTranslateConfigDTO result = new SemanticTranslateConfigDTO();
        when(service.upsertAdminConfig(eq(payload), eq(null), eq(null))).thenReturn(result);
        controller.upsertConfig(payload, null);
        verify(service).upsertAdminConfig(eq(payload), eq(null), eq(null));

        Principal principal = () -> "admin";
        UsersEntity user = new UsersEntity();
        user.setId(14L);
        when(administratorService.findByUsername("admin")).thenReturn(Optional.of(user)).thenReturn(Optional.empty());
        when(service.upsertAdminConfig(eq(payload), eq(14L), eq("admin"))).thenReturn(result);
        when(service.upsertAdminConfig(eq(payload), eq(null), eq("admin"))).thenReturn(result);

        controller.upsertConfig(payload, principal);
        controller.upsertConfig(payload, principal);

        verify(service).upsertAdminConfig(eq(payload), eq(14L), eq("admin"));
        verify(service).upsertAdminConfig(eq(payload), eq(null), eq("admin"));
    }
}
