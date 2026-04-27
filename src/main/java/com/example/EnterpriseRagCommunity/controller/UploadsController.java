package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitRequestDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadStatusDTO;
import com.example.EnterpriseRagCommunity.service.monitor.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class UploadsController {
    private static final String HEADER_UPLOAD_OFFSET = "X-Upload-Offset";
    private static final String HEADER_UPLOAD_TOTAL = "X-Upload-Total";

    private final UploadService uploadService;

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
    public ResumableUploadStatusDTO getResumableStatus(@PathVariable String uploadId) {
        return uploadService.getResumableStatus(uploadId);
    }

    @PutMapping("/resumable/{uploadId}/chunk")
    public ResumableUploadStatusDTO uploadResumableChunk(
            @PathVariable String uploadId,
            HttpServletRequest request
    ) throws IOException {
        long offset = parseRequiredLongHeader(request, HEADER_UPLOAD_OFFSET);
        long total = parseRequiredLongHeader(request, HEADER_UPLOAD_TOTAL);
        return uploadService.uploadResumableChunk(uploadId, offset, total, request.getInputStream());
    }

    @PostMapping("/resumable/{uploadId}/complete")
    public UploadResultDTO completeResumable(@PathVariable String uploadId) {
        return uploadService.completeResumable(uploadId);
    }

    @DeleteMapping("/resumable/{uploadId}")
    public void cancelResumable(@PathVariable String uploadId) {
        uploadService.cancelResumable(uploadId);
    }

    private static long parseRequiredLongHeader(HttpServletRequest request, String headerName) {
        String raw = request.getHeader(headerName);
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header: " + headerName);
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid header: " + headerName, ex);
        }
    }
}
