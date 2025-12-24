package com.example.EnterpriseRagCommunity.dto.content;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PostToggleResponseDTO {
    private boolean likedByMe;
    private boolean favoritedByMe;
    private long reactionCount;
    private long favoriteCount;
}

