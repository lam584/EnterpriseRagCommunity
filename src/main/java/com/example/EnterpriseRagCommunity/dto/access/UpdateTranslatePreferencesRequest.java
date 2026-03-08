package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @JsonIgnore
    private boolean titleGenCountPresent;

    @Min(value = 1, message = "titleGenCount 需为 1~50 的整数")
    @Max(value = 50, message = "titleGenCount 需为 1~50 的整数")
    private Integer titleGenCount;

    @JsonIgnore
    private boolean tagGenCountPresent;

    @Min(value = 1, message = "tagGenCount 需为 1~50 的整数")
    @Max(value = 50, message = "tagGenCount 需为 1~50 的整数")
    private Integer tagGenCount;

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

    public void setTitleGenCount(Integer titleGenCount) {
        this.titleGenCountPresent = true;
        this.titleGenCount = titleGenCount;
    }

    public void setTagGenCount(Integer tagGenCount) {
        this.tagGenCountPresent = true;
        this.tagGenCount = tagGenCount;
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

    public boolean isTitleGenCountPresent() {
        return titleGenCountPresent;
    }

    public boolean isTagGenCountPresent() {
        return tagGenCountPresent;
    }
}
