package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationLlmAutoRunnerChunkImageSelectionTest {

    @Test
    void shouldSelectReferencedImages_sortedByIndex_andDedupByUrl() {
        String metaJson = """
                {
                  "extractedImages": [
                    { "index": 1, "url": "http://x/a.png", "mimeType": "image/png", "placeholder": "[[IMAGE_1]]" },
                    { "index": "2", "url": "http://x/b.png", "mimeType": "image/png", "placeholder": "[[IMAGE_2]]" },
                    { "index": 3, "url": "http://x/b.png", "mimeType": "image/png", "placeholder": "[[IMAGE_3]]" },
                    { "placeholder": "[[IMAGE_99]]", "url": "http://x/c.png", "mimeType": "image/png" }
                  ]
                }
                """;
        String chunkText = "hello [[IMAGE_2]] world [[IMAGE_1]]";

        var imgs = ModerationLlmAutoRunner.selectChunkImageInputs(new ObjectMapper(), chunkText, 10L, metaJson);
        assertEquals(2, imgs.size());
        assertEquals(10L, imgs.get(0).getFileAssetId());
        assertEquals("http://x/a.png", imgs.get(0).getUrl());
        assertEquals("http://x/b.png", imgs.get(1).getUrl());
    }

    @Test
    void shouldReturnEmptyWhenNoPlaceholders() {
        String metaJson = """
                { "extractedImages": [ { "index": 1, "url": "http://x/a.png", "mimeType": "image/png" } ] }
                """;
        var imgs = ModerationLlmAutoRunner.selectChunkImageInputs(new ObjectMapper(), "no images here", 10L, metaJson);
        assertEquals(0, imgs.size());
    }
}

