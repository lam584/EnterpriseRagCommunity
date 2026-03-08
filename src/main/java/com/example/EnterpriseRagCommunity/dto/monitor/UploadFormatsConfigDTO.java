package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UploadFormatsConfigDTO {
    private Boolean enabled;
    private Integer maxFilesPerRequest;
    private Long maxFileSizeBytes;
    private Long maxTotalSizeBytes;
    private Integer parseTimeoutMillis;
    private Long parseMaxChars;
    private List<UploadFormatRuleDTO> formats;

    public static UploadFormatsConfigDTO empty() {
        UploadFormatsConfigDTO dto = new UploadFormatsConfigDTO();
        dto.setFormats(new ArrayList<>());
        return dto;
    }

    @Data
    public static class UploadFormatRuleDTO {
        private String format;
        private Boolean enabled;
        private List<String> extensions;
        private Long maxFileSizeBytes;
        private Boolean parseEnabled;
    }
}
