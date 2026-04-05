package com.example.EnterpriseRagCommunity.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiResponseParsingUtilsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseStringArrayField_should_extract_object_body_and_deduplicate() {
        List<String> languages = AiResponseParsingUtils.parseStringArrayField(
                objectMapper,
                "xx {\"languages\":[\" EN \",\"en\",\"zh\",123]} yy",
                "languages",
                value -> value == null ? "" : value.trim().toLowerCase(),
                "bad payload",
                2
        );

        assertEquals(List.of("en", "zh"), languages);
    }

    @Test
    void parseStringArrayField_should_throw_with_wrapped_error_when_json_invalid() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AiResponseParsingUtils.parseStringArrayField(
                        objectMapper,
                        "{\"languages\":",
                        "languages",
                        value -> value,
                        "bad payload",
                        3
                )
        );

        assertEquals("bad payload", ex.getMessage());
    }
}
