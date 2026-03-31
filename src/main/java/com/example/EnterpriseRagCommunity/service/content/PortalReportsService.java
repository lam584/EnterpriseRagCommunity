package com.example.EnterpriseRagCommunity.service.content;

import lombok.Getter;

public interface PortalReportsService {
    ReportSubmitResult reportPost(Long postId, String reasonCode, String reasonText);
    ReportSubmitResult reportComment(Long commentId, String reasonCode, String reasonText);
    ReportSubmitResult reportProfile(Long userId, String reasonCode, String reasonText);

    @Getter
    class ReportSubmitResult {
        private final Long reportId;
        private final Long queueId;

        public ReportSubmitResult(Long reportId, Long queueId) {
            this.reportId = reportId;
            this.queueId = queueId;
        }

    }
}
