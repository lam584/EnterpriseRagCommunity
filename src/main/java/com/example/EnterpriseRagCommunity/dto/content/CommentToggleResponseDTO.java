package com.example.EnterpriseRagCommunity.dto.content;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommentToggleResponseDTO {
    private boolean likedByMe;
    private long likeCount;
}

