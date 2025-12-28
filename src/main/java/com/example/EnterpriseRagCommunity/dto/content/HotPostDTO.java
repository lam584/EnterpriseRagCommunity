package com.example.EnterpriseRagCommunity.dto.content;

import lombok.Data;

/**
 * Hot榜单返回给前台的DTO：PostDetailDTO + 当前窗口分数。
 */
@Data
public class HotPostDTO {
    private PostDetailDTO post;
    private Double score;
}

