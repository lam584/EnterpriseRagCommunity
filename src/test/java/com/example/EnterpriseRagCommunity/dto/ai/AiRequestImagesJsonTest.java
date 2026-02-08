package com.example.EnterpriseRagCommunity.dto.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AiRequestImagesJsonTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void aiChatStreamRequest_deserializeImages() throws Exception {
        String json = """
                {
                  "message": "hi",
                  "deepThink": false,
                  "useRag": true,
                  "dryRun": false,
                  "images": [
                    { "url": "/uploads/a.png", "mimeType": "image/png" }
                  ]
                }
                """;
        AiChatStreamRequest req = om.readValue(json, AiChatStreamRequest.class);
        assertNotNull(req.getImages());
        assertEquals(1, req.getImages().size());
        assertEquals("/uploads/a.png", req.getImages().get(0).getUrl());
        assertEquals("image/png", req.getImages().get(0).getMimeType());
    }

    @Test
    void aiPostComposeStreamRequest_deserializeImages() throws Exception {
        String json = """
                {
                  "snapshotId": 123,
                  "deepThink": false,
                  "instruction": "rewrite",
                  "images": [
                    { "url": "https://example.com/a.jpg", "mimeType": "image/jpeg" }
                  ]
                }
                """;
        AiPostComposeStreamRequest req = om.readValue(json, AiPostComposeStreamRequest.class);
        assertNotNull(req.getImages());
        assertEquals(1, req.getImages().size());
        assertEquals("https://example.com/a.jpg", req.getImages().get(0).getUrl());
        assertEquals("image/jpeg", req.getImages().get(0).getMimeType());
    }
}

