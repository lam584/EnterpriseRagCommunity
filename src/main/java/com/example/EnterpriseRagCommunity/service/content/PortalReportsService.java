package com.example.EnterpriseRagCommunity.service.content;

public interface PortalReportsService {
    ReportSubmitResult reportPost(Long postId, String reasonCode, String reasonText);
    ReportSubmitResult reportComment(Long commentId, String reasonCode, String reasonText);
    ReportSubmitResult reportProfile(Long userId, String reasonCode, String reasonText);

    record ReportSubmitResult(Long reportId, Long queueId) {

    }
}
