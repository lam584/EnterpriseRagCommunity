package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadStatusDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.service.monitor.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UploadsController.class)
@AutoConfigureMockMvc(addFilters = false)
class UploadsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadService uploadService;

    @Test
    void upload_shouldReturn200() throws Exception {
        UploadResultDTO out = new UploadResultDTO();
        out.setId(1L);
        out.setFileName("a.txt");

        when(uploadService.upload(any())).thenReturn(out);

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        mockMvc.perform(multipart("/api/uploads").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fileName").value("a.txt"));
    }

    @Test
    void uploadBatch_shouldReturn200() throws Exception {
        UploadResultDTO a = new UploadResultDTO();
        a.setId(1L);
        UploadResultDTO b = new UploadResultDTO();
        b.setId(2L);

        when(uploadService.uploadMany(any())).thenReturn(List.of(a, b));

        MockMultipartFile f1 = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());

        mockMvc.perform(multipart("/api/uploads/batch").file(f1).file(f2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void findBySha256_shouldReturn200_withOptionalFileName() throws Exception {
        UploadResultDTO out = new UploadResultDTO();
        out.setId(1L);
        out.setFileName("a.txt");

        when(uploadService.findBySha256("abc", null)).thenReturn(out);

        mockMvc.perform(get("/api/uploads/by-sha256").param("sha256", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        when(uploadService.findBySha256("abc", "a.txt")).thenReturn(out);

        mockMvc.perform(get("/api/uploads/by-sha256").param("sha256", "abc").param("fileName", "a.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void initResumable_shouldReturn200() throws Exception {
        ResumableUploadInitResponseDTO resp = new ResumableUploadInitResponseDTO();
        resp.setUploadId("u1");
        resp.setChunkSizeBytes(1024);
        resp.setUploadedBytes(0L);

        when(uploadService.initResumable(any())).thenReturn(resp);

        mockMvc.perform(post("/api/uploads/resumable/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"a.txt\",\"fileSize\":10,\"mimeType\":\"text/plain\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value("u1"));
    }

    @Test
    void getResumableStatus_shouldReturn200() throws Exception {
        ResumableUploadStatusDTO resp = new ResumableUploadStatusDTO();
        resp.setUploadId("u1");
        resp.setUploadedBytes(5L);
        resp.setStatus("UPLOADING");

        when(uploadService.getResumableStatus("u1")).thenReturn(resp);

        mockMvc.perform(get("/api/uploads/resumable/u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value("u1"))
                .andExpect(jsonPath("$.status").value("UPLOADING"));
    }

    @Test
    void uploadResumableChunk_shouldReturn200() throws Exception {
        ResumableUploadStatusDTO resp = new ResumableUploadStatusDTO();
        resp.setUploadId("u1");
        resp.setUploadedBytes(10L);
        resp.setStatus("UPLOADING");

        when(uploadService.uploadResumableChunk(eq("u1"), eq(0L), eq(10L), any())).thenReturn(resp);

        mockMvc.perform(put("/api/uploads/resumable/u1/chunk")
                        .header("X-Upload-Offset", "0")
                        .header("X-Upload-Total", "10")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("0123456789".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedBytes").value(10));
    }

    @Test
    void uploadResumableChunk_shouldReturn400_whenRequiredHeaderMissing() throws Exception {
        mockMvc.perform(put("/api/uploads/resumable/u1/chunk")
                        .header("X-Upload-Offset", "0")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("0123456789".getBytes()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeResumable_shouldReturn200() throws Exception {
        UploadResultDTO out = new UploadResultDTO();
        out.setId(9L);
        when(uploadService.completeResumable("u1")).thenReturn(out);

        mockMvc.perform(post("/api/uploads/resumable/u1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void cancelResumable_shouldReturn200() throws Exception {
        doNothing().when(uploadService).cancelResumable("u1");

        mockMvc.perform(delete("/api/uploads/resumable/u1"))
                .andExpect(status().isOk());
    }
}
