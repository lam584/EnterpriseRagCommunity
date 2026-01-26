package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTranslatePreferencesRequest {

    @JsonIgnore
    private boolean targetLanguagePresent;

    @Size(max = 32, message = "targetLanguage长度不能超过32")
    private String targetLanguage;

    @JsonIgnore
    private boolean autoTranslatePostsPresent;

    private Boolean autoTranslatePosts;

    @JsonIgnore
    private boolean autoTranslateCommentsPresent;

    private Boolean autoTranslateComments;

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguagePresent = true;
        this.targetLanguage = targetLanguage;
    }

    public void setAutoTranslatePosts(Boolean autoTranslatePosts) {
        this.autoTranslatePostsPresent = true;
        this.autoTranslatePosts = autoTranslatePosts;
    }

    public void setAutoTranslateComments(Boolean autoTranslateComments) {
        this.autoTranslateCommentsPresent = true;
        this.autoTranslateComments = autoTranslateComments;
    }

    public boolean isTargetLanguagePresent() {
        return targetLanguagePresent;
    }

    public boolean isAutoTranslatePostsPresent() {
        return autoTranslatePostsPresent;
    }

    public boolean isAutoTranslateCommentsPresent() {
        return autoTranslateCommentsPresent;
    }
}

