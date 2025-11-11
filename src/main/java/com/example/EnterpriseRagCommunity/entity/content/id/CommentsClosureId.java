package com.example.EnterpriseRagCommunity.entity.content.id;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class CommentsClosureId implements Serializable {
    private Long ancestorId;
    private Long descendantId;
}
