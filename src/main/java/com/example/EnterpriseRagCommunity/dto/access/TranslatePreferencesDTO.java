package com.example.EnterpriseRagCommunity.dto.access;

import lombok.Data;

@Data
public class TranslatePreferencesDTO {
    private String targetLanguage;
    private Boolean autoTranslatePosts;
    private Boolean autoTranslateComments;
    private Integer titleGenCount;
    private Integer tagGenCount;
}
