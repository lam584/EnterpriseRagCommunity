package com.example.EnterpriseRagCommunity.dto.content;

import lombok.Data;

@Data
public class HotScoreConfigDTO {
    private Double likeWeight;
    private Double favoriteWeight;
    private Double commentWeight;
    private Double viewWeight;
    private Double allDecayDays;
    private Boolean autoRefreshEnabled;
    private Integer autoRefreshIntervalMinutes;
}
