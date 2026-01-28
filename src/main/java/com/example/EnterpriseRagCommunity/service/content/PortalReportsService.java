package com.example.EnterpriseRagCommunity.service.content;

public interface PortalReportsService {
    ReportSubmitResult reportPost(Long postId, String reasonCode, String reasonText);
    ReportSubmitResult reportComment(Long commentId, String reasonCode, String reasonText);

    class ReportSubmitResult {
        private final Long reportId;
        private final Long queueId;

        public ReportSubmitResult(Long reportId, Long queueId) {
            this.reportId = reportId;
            this.queueId = queueId;
        }

        public Long getReportId() {
            return reportId;
        }

        public Long getQueueId() {
            return queueId;
        }
    }
}
