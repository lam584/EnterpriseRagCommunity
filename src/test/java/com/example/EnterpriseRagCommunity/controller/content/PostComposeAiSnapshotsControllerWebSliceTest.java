package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotDTO;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import com.example.EnterpriseRagCommunity.service.content.PostComposeAiSnapshotsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PostComposeAiSnapshotsController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostComposeAiSnapshotsControllerWebSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostComposeAiSnapshotsService service;

    @Test
    void getPending_shouldReturn200_whenServiceReturnsDto() throws Exception {
        PostComposeAiSnapshotDTO dto = new PostComposeAiSnapshotDTO();
        dto.setId(1L);

        when(service.getPending(eq(PostComposeAiSnapshotTargetType.DRAFT), eq(10L), isNull())).thenReturn(dto);

        mockMvc.perform(get("/api/post-compose/ai-snapshots/pending")
                        .param("targetType", "DRAFT")
                        .param("draftId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getPending_shouldReturn404_whenServiceReturnsNull() throws Exception {
        when(service.getPending(eq(PostComposeAiSnapshotTargetType.DRAFT), eq(10L), isNull())).thenReturn(null);

        mockMvc.perform(get("/api/post-compose/ai-snapshots/pending")
                        .param("targetType", "DRAFT")
                        .param("draftId", "10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void apply_shouldReturn400_whenAfterContentBlank() throws Exception {
        mockMvc.perform(post("/api/post-compose/ai-snapshots/1/apply")
                        .contentType("application/json")
                        .content("{\"afterContent\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.afterContent").exists());

        verifyNoInteractions(service);
    }
}

