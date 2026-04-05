package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiChatInputSupportTest {

    @Test
    void appendImageUrlsAsText_should_skip_null_blank_and_limit_to_five() {
        List<Map<String, Object>> images = new ArrayList<>();
        images.add(null);
        images.add(Map.of("url", " "));
        for (int i = 1; i <= 6; i++) {
            images.add(Map.of("url", " https://img/" + i + ".png "));
        }

        String out = AiChatInputSupport.appendImageUrlsAsText("base", images, image -> image == null ? null : image.get("url"));

        assertEquals("""
                base

                [IMAGES]
                - https://img/1.png
                - https://img/2.png
                - https://img/3.png
                - https://img/4.png
                - https://img/5.png
                """, out);
    }
}
