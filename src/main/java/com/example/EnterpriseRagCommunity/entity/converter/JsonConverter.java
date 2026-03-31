package com.example.EnterpriseRagCommunity.entity.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;
import java.util.List;

@Converter
public class JsonConverter implements AttributeConverter<Object, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Object attribute) {
        if (attribute == null) return null;
        if (attribute instanceof String s) return s;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize JSON attribute", e);
        }
    }

    @Override
    public Object convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            // Parse generic JSON; return Map for objects, List for arrays
            Object parsed = MAPPER.readValue(dbData, Object.class);
            if (parsed instanceof Map || parsed instanceof List) {
                return parsed; // expected Map<String,Object> in entity definitions
            }
            return parsed; // primitives
        } catch (Exception e) {
            // Fallback to raw string if not valid JSON
            return dbData;
        }
    }
}
