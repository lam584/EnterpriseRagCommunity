package com.example.EnterpriseRagCommunity.dto.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.enums.CostScope;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.SystemEventLevel;
import com.example.EnterpriseRagCommunity.service.monitor.LogRetentionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorQueryDtoCoverageTest {

    @Test
    void logRetentionConfigRecord_shouldExposeComponents() {
        LogRetentionConfigDTO dto = new LogRetentionConfigDTO(true, 30, LogRetentionMode.DELETE);

        assertThat(dto.enabled()).isTrue();
        assertThat(dto.keepDays()).isEqualTo(30);
        assertThat(dto.mode()).isEqualTo(LogRetentionMode.DELETE);
    }

    @Test
    void queryDtos_shouldApplyExpectedDefaultSort() {
        CostRecordsQueryDTO costRecordsQueryDTO = new CostRecordsQueryDTO();
        RagEvalResultsQueryDTO ragEvalResultsQueryDTO = new RagEvalResultsQueryDTO();
        FileAssetsQueryDTO fileAssetsQueryDTO = new FileAssetsQueryDTO();
        SearchLogsQueryDTO searchLogsQueryDTO = new SearchLogsQueryDTO();
        RagEvalRunsQueryDTO ragEvalRunsQueryDTO = new RagEvalRunsQueryDTO();
        NotificationsQueryDTO notificationsQueryDTO = new NotificationsQueryDTO();
        ReviewEfficiencyQueryDTO reviewEfficiencyQueryDTO = new ReviewEfficiencyQueryDTO();
        SystemEventsQueryDTO systemEventsQueryDTO = new SystemEventsQueryDTO();
        MetricsEventsQueryDTO metricsEventsQueryDTO = new MetricsEventsQueryDTO();
        UserSettingsQueryDTO userSettingsQueryDTO = new UserSettingsQueryDTO();
        RagEvalSamplesQueryDTO ragEvalSamplesQueryDTO = new RagEvalSamplesQueryDTO();

        assertThat(costRecordsQueryDTO.getOrderBy()).isEqualTo("ts");
        assertThat(costRecordsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(ragEvalResultsQueryDTO.getOrderBy()).isEqualTo("createdAt");
        assertThat(ragEvalResultsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(fileAssetsQueryDTO.getOrderBy()).isEqualTo("createdAt");
        assertThat(fileAssetsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(searchLogsQueryDTO.getOrderBy()).isEqualTo("createdAt");
        assertThat(searchLogsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(ragEvalRunsQueryDTO.getOrderBy()).isEqualTo("createdAt");
        assertThat(ragEvalRunsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(notificationsQueryDTO.getOrderBy()).isEqualTo("createdAt");
        assertThat(notificationsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(reviewEfficiencyQueryDTO.getOrderBy()).isEqualTo("createdAt");
        assertThat(reviewEfficiencyQueryDTO.getSort()).isEqualTo("desc");
        assertThat(systemEventsQueryDTO.getOrderBy()).isEqualTo("createdAt");
        assertThat(systemEventsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(metricsEventsQueryDTO.getOrderBy()).isEqualTo("ts");
        assertThat(metricsEventsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(userSettingsQueryDTO.getOrderBy()).isEqualTo("id");
        assertThat(userSettingsQueryDTO.getSort()).isEqualTo("desc");
        assertThat(ragEvalSamplesQueryDTO.getOrderBy()).isEqualTo("createdAt");
        assertThat(ragEvalSamplesQueryDTO.getSort()).isEqualTo("desc");
    }

    @Test
    void queryDtos_shouldAllowBasicFieldAssignments() {
        CostRecordsQueryDTO costRecordsQueryDTO = new CostRecordsQueryDTO();
        costRecordsQueryDTO.setScope(CostScope.GEN);
        assertThat(costRecordsQueryDTO.getScope()).isEqualTo(CostScope.GEN);

        SystemEventsQueryDTO systemEventsQueryDTO = new SystemEventsQueryDTO();
        systemEventsQueryDTO.setLevel(SystemEventLevel.ERROR);
        assertThat(systemEventsQueryDTO.getLevel()).isEqualTo(SystemEventLevel.ERROR);

        UploadFormatsConfigDTO dto = UploadFormatsConfigDTO.empty();
        assertThat(dto.getFormats()).isEmpty();

        UploadFormatsConfigDTO.UploadFormatRuleDTO ruleDTO = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        ruleDTO.setFormat("pdf");
        ruleDTO.setEnabled(true);
        ruleDTO.setExtensions(List.of(".pdf"));
        dto.setFormats(List.of(ruleDTO));

        assertThat(dto.getFormats()).hasSize(1);
        assertThat(dto.getFormats().get(0).getFormat()).isEqualTo("pdf");
        assertThat(dto.getFormats().get(0).getEnabled()).isTrue();
        assertThat(dto.getFormats().get(0).getExtensions()).containsExactly(".pdf");
    }
}
