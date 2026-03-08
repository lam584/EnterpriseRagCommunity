package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitRequestDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadStatusDTO;
import com.example.EnterpriseRagCommunity.service.monitor.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class UploadsController {

    @Autowired
    private UploadService uploadService;

    @PostMapping
    public UploadResultDTO upload(@RequestParam("file") MultipartFile file) {
        return uploadService.upload(file);
    }

    @PostMapping("/batch")
    public List<UploadResultDTO> uploadBatch(@RequestParam("files") List<MultipartFile> files) {
        return uploadService.uploadMany(files);
    }

    @GetMapping("/by-sha256")
    public UploadResultDTO findBySha256(
            @RequestParam("sha256") String sha256,
            @RequestParam(value = "fileName", required = false) String fileName
    ) {
        return uploadService.findBySha256(sha256, fileName);
    }

    @PostMapping("/resumable/init")
    public ResumableUploadInitResponseDTO initResumable(@RequestBody ResumableUploadInitRequestDTO req) {
        return uploadService.initResumable(req);
    }

    @GetMapping("/resumable/{uploadId}")
    public ResumableUploadStatusDTO getResumableStatus(@PathVariable("uploadId") String uploadId) {
        return uploadService.getResumableStatus(uploadId);
    }

    @PutMapping("/resumable/{uploadId}/chunk")
    public ResumableUploadStatusDTO uploadResumableChunk(
            @PathVariable("uploadId") String uploadId,
            @RequestHeader("X-Upload-Offset") long offset,
            @RequestHeader("X-Upload-Total") long total,
            HttpServletRequest request
    ) throws IOException {
        return uploadService.uploadResumableChunk(uploadId, offset, total, request.getInputStream());
    }

    @PostMapping("/resumable/{uploadId}/complete")
    public UploadResultDTO completeResumable(@PathVariable("uploadId") String uploadId) {
        return uploadService.completeResumable(uploadId);
    }

    @DeleteMapping("/resumable/{uploadId}")
    public void cancelResumable(@PathVariable("uploadId") String uploadId) {
        uploadService.cancelResumable(uploadId);
    }
}

