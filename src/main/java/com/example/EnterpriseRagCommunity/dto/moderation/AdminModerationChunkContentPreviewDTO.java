package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminModerationChunkContentPreviewDTO {
    private Source source;
    private String text;
    private String reason;
    private List<Image> images = new ArrayList<>();

    @Data
    public static class Source {
        private Long chunkId;
        private Long queueId;
        private String contentType;
        private Long contentId;
        private String sourceType;
        private Long fileAssetId;
        private Integer startOffset;
        private Integer endOffset;
    }

    @Data
    public static class Image {
        private Integer index;
        private String placeholder;
        private String url;
        private String mimeType;
        private String fileName;
        private Long sizeBytes;
        private Long fileAssetId;
        private Integer width;
        private Integer height;
    }
}
