package com.example.EnterpriseRagCommunity.service.ai.client;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenAiCompatClientMultimodalBodyTest {

    @Test
    void buildBodyJson_supportsMultimodalContentParts() throws Exception {
        Method m = OpenAiCompatClient.class.getDeclaredMethod(
                "buildBodyJson",
                String.class,
                List.class,
                Double.class,
                Double.class,
                Integer.class,
                List.class,
                Boolean.class,
                Integer.class,
                Map.class,
                boolean.class
        );
        m.setAccessible(true);

        List<Map<String, Object>> parts = List.of(
                Map.of("type", "text", "text", "hi"),
                Map.of("type", "image_url", "image_url", Map.of("url", "https://example.com/a.png"))
        );
        List<ChatMessage> messages = List.of(new ChatMessage("user", parts));

        String body = (String) m.invoke(null, "gpt-4o", messages, null, null, null, null, null, null, null, false);

        assertTrue(body.contains("\"messages\":["));
        assertTrue(body.contains("\"content\":["));
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("https://example.com/a.png"));
    }

    @Test
    void buildBodyJson_supportsTextContent() throws Exception {
        Method m = OpenAiCompatClient.class.getDeclaredMethod(
                "buildBodyJson",
                String.class,
                List.class,
                Double.class,
                Double.class,
                Integer.class,
                List.class,
                Boolean.class,
                Integer.class,
                Map.class,
                boolean.class
        );
        m.setAccessible(true);

        List<ChatMessage> messages = List.of(ChatMessage.user("hello"));
        String body = (String) m.invoke(null, "gpt-4o", messages, null, null, null, null, null, null, null, false);

        assertTrue(body.contains("\"content\":\"hello\""));
    }
}
