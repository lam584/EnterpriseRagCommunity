package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitRequestDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadStatusDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

public interface UploadService {
    UploadResultDTO upload(MultipartFile file);

    List<UploadResultDTO> uploadMany(List<MultipartFile> files);

    UploadResultDTO findBySha256(String sha256, String fileName);

    ResumableUploadInitResponseDTO initResumable(ResumableUploadInitRequestDTO req);

    ResumableUploadStatusDTO getResumableStatus(String uploadId);

    ResumableUploadStatusDTO uploadResumableChunk(String uploadId, long offset, long total, InputStream bodyStream);

    UploadResultDTO completeResumable(String uploadId);

    void cancelResumable(String uploadId);
}

