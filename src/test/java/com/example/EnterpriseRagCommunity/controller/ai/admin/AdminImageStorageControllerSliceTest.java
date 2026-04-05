package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.repository.ai.ImageUploadLogRepository;
import com.example.EnterpriseRagCommunity.service.ai.ImageStorageConfigService;
import com.example.EnterpriseRagCommunity.service.ai.LlmImageUploadService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminImageStorageController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminImageStorageControllerSliceTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @MockitoBean
    private ImageStorageConfigService configService;

    @MockitoBean
    private ImageUploadLogRepository uploadLogRepository;

    @MockitoBean
    private LlmImageUploadService uploadService;

    @Test
    void testUpload_shouldPassValidatedRelativePathToService() throws Exception {
        when(uploadService.resolveImageUrl(any(LlmImageUploadService.ValidatedLocalPath.class), eq("image/png"), eq("model-1")))
                .thenReturn("https://cdn.example.com/uploads/2026/01/a.png");

        mockMvc.perform(post("/api/admin/ai/image-storage/test-upload")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "localPath": "uploads/2026/01/a.png",
                                  "mimeType": "image/png",
                                  "modelName": "model-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.remoteUrl").value("https://cdn.example.com/uploads/2026/01/a.png"));

        ArgumentCaptor<LlmImageUploadService.ValidatedLocalPath> captor =
                ArgumentCaptor.forClass(LlmImageUploadService.ValidatedLocalPath.class);
        verify(uploadService).resolveImageUrl(captor.capture(), eq("image/png"), eq("model-1"));
        assertEquals("uploads/2026/01/a.png", captor.getValue().value());
    }

    @Test
    void testUpload_shouldRejectAbsoluteOrTraversalPaths() throws Exception {
        mockMvc.perform(post("/api/admin/ai/image-storage/test-upload")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "localPath": "../secret.txt",
                                  "mimeType": "image/png",
                                  "modelName": "model-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("localPath 不能为空"));

        verify(uploadService, never()).resolveImageUrl(any(LlmImageUploadService.ValidatedLocalPath.class), any(), any());
    }
}
