package com.example.EnterpriseRagCommunity.service.content.admin;

import com.example.EnterpriseRagCommunity.dto.content.admin.CommentAdminDTO;
import com.example.EnterpriseRagCommunity.dto.content.admin.CommentSetDeletedRequest;
import com.example.EnterpriseRagCommunity.dto.content.admin.CommentUpdateStatusRequest;
import org.springframework.data.domain.Page;

public interface AdminCommentsService {
    Page<CommentAdminDTO> list(int page,
                              int pageSize,
                              Long postId,
                              Long authorId,
                              String authorName,
                              java.time.LocalDateTime createdFrom,
                              java.time.LocalDateTime createdTo,
                              String status,
                              Boolean isDeleted,
                              String keyword);

    CommentAdminDTO updateStatus(Long id, CommentUpdateStatusRequest req);

    CommentAdminDTO setDeleted(Long id, CommentSetDeletedRequest req);
}
