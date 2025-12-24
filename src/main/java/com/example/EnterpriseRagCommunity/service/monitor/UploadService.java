package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import org.springframework.web.multipart.MultipartFile;

public interface UploadService {
    UploadResultDTO upload(MultipartFile file);
}

