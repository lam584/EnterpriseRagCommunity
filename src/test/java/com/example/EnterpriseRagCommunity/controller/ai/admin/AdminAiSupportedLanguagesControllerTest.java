package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.SupportedLanguageDTO;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.service.ai.SupportedLanguageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAiSupportedLanguagesControllerTest {

    @Test
    void update_shouldRejectChangingDefaultLanguageCode() {
        SupportedLanguageService supportedLanguageService = mock(SupportedLanguageService.class);
        AdminAiSupportedLanguagesController controller = new AdminAiSupportedLanguagesController(supportedLanguageService);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("en");

        assertThrows(IllegalArgumentException.class, () -> controller.update(SupportedLanguageService.DEFAULT_LANGUAGE_CODE, payload));
    }

    @Test
    void update_shouldTrimCodes_andDelegateToService() {
        SupportedLanguageService supportedLanguageService = mock(SupportedLanguageService.class);
        AdminAiSupportedLanguagesController controller = new AdminAiSupportedLanguagesController(supportedLanguageService);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("  zh-CN  ");

        SupportedLanguageDTO ok = new SupportedLanguageDTO();
        when(supportedLanguageService.adminUpdate("zh-CN", payload)).thenReturn(ok);

        SupportedLanguageDTO got = controller.update("  zh-CN  ", payload);
        assertSame(ok, got);
        verify(supportedLanguageService).adminUpdate("zh-CN", payload);
    }

    @Test
    void update_shouldMapLanguageNotFoundToResourceNotFound() {
        SupportedLanguageService supportedLanguageService = mock(SupportedLanguageService.class);
        AdminAiSupportedLanguagesController controller = new AdminAiSupportedLanguagesController(supportedLanguageService);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("zh-CN");

        when(supportedLanguageService.adminUpdate("x", payload)).thenThrow(new IllegalArgumentException("语言不存在: x"));

        assertThrows(ResourceNotFoundException.class, () -> controller.update("x", payload));
    }

    @Test
    void update_shouldRethrowOtherIllegalArgumentException() {
        SupportedLanguageService supportedLanguageService = mock(SupportedLanguageService.class);
        AdminAiSupportedLanguagesController controller = new AdminAiSupportedLanguagesController(supportedLanguageService);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("zh-CN");

        IllegalArgumentException ex = new IllegalArgumentException((String) null);
        when(supportedLanguageService.adminUpdate("x", payload)).thenThrow(ex);

        IllegalArgumentException got = assertThrows(IllegalArgumentException.class, () -> controller.update("x", payload));
        assertSame(ex, got);
    }

    @Test
    void delete_shouldRejectDeletingDefaultLanguage() {
        SupportedLanguageService supportedLanguageService = mock(SupportedLanguageService.class);
        AdminAiSupportedLanguagesController controller = new AdminAiSupportedLanguagesController(supportedLanguageService);

        assertThrows(IllegalArgumentException.class, () -> controller.delete(SupportedLanguageService.DEFAULT_LANGUAGE_CODE));
    }

    @Test
    void delete_shouldMapLanguageNotFoundToResourceNotFound() {
        SupportedLanguageService supportedLanguageService = mock(SupportedLanguageService.class);
        AdminAiSupportedLanguagesController controller = new AdminAiSupportedLanguagesController(supportedLanguageService);

        doThrow(new IllegalArgumentException("语言不存在: x")).when(supportedLanguageService).adminDeactivate("x");
        assertThrows(ResourceNotFoundException.class, () -> controller.delete("x"));
    }

    @Test
    void delete_shouldReturnOk_onSuccess() {
        SupportedLanguageService supportedLanguageService = mock(SupportedLanguageService.class);
        AdminAiSupportedLanguagesController controller = new AdminAiSupportedLanguagesController(supportedLanguageService);

        ResponseEntity<Void> resp = controller.delete("  en  ");

        assertEquals(200, resp.getStatusCode().value());
        verify(supportedLanguageService).adminDeactivate("en");
    }
}

