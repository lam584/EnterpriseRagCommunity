package com.example.EnterpriseRagCommunity.service.access;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public final class DateTimeParamSupport {

    private DateTimeParamSupport() {
    }

    public static LocalDateTime parseOrNull(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
