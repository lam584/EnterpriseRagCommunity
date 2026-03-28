package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceCommentCitationTest {

    @Test
    void assemble_should_preserve_comment_id_and_build_comment_anchor_url() {
        RagContextPromptService service = new RagContextPromptService();

        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(12L);
        hit.setCommentId(34L);
        hit.setChunkIndex(2);
        hit.setScore(0.88);
        hit.setTitle("帖子引用");
        hit.setContentText("这是一条来自评论区的参考资料。");

        ContextClipConfigDTO clipCfg = new ContextClipConfigDTO();
        clipCfg.setEnabled(true);
        clipCfg.setMaxItems(1);

        CitationConfigDTO citationCfg = new CitationConfigDTO();
        citationCfg.setEnabled(true);
        citationCfg.setCitationMode("BOTH");
        citationCfg.setMaxSources(3);
        citationCfg.setIncludeUrl(true);
        citationCfg.setSourcesTitle("来源");
        citationCfg.setPostUrlTemplate("/portal/posts/detail/{postId}");

        RagContextPromptService.AssembleResult assembled = service.assemble("问题", List.of(hit), clipCfg, citationCfg);
        assertEquals(1, assembled.getSources().size());

        RagContextPromptService.CitationSource source = assembled.getSources().get(0);
        assertEquals(12L, source.getPostId());
        assertEquals(34L, source.getCommentId());
        assertEquals("/portal/posts/detail/12?commentId=34#comment-34", source.getUrl());

        String sourcesText = RagContextPromptService.renderSourcesText(citationCfg, assembled.getSources());
        assertTrue(sourcesText.contains("comment_id=34"));
    }
}
