package com.example.EnterpriseRagCommunity.service.content.admin;

import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminDetailDTO;
import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminListItemDTO;
import org.springframework.data.domain.Page;

public interface AdminPostFilesService {
    Page<PostFileExtractionAdminListItemDTO> list(int page, int pageSize, Long postId, Long fileAssetId, String keyword, String extractStatus);

    PostFileExtractionAdminDetailDTO detail(Long attachmentId);

    PostFileExtractionAdminDetailDTO reextract(Long attachmentId);
}

