package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class ChatContextGovernanceConfigDTO {
    private Boolean enabled;

    private Integer maxPromptTokens;
    private Integer reserveAnswerTokens;
    private Integer maxPromptChars;
    private Integer perMessageMaxTokens;
    private Integer keepLastMessages;

    private Boolean allowDropRagContext;

    private Boolean compressionEnabled;
    private Integer compressionTriggerTokens;
    private Integer compressionKeepLastMessages;
    private Integer compressionPerMessageSnippetChars;
    private Integer compressionMaxChars;

    private Integer maxFiles;
    private Integer perFileMaxChars;
    private Integer totalFilesMaxChars;

    private Boolean logEnabled;
    private Double logSampleRate;
    private Integer logMaxDays;
}
