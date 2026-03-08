package com.example.EnterpriseRagCommunity.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadPropertiesTest {

    @Test
    void normalizedUrlPrefix_should_default_when_blank_or_null() {
        UploadProperties p1 = new UploadProperties();
        p1.setUrlPrefix(null);
        assertEquals("/uploads", p1.normalizedUrlPrefix());

        UploadProperties p2 = new UploadProperties();
        p2.setUrlPrefix("   ");
        assertEquals("/uploads", p2.normalizedUrlPrefix());
    }

    @Test
    void normalizedUrlPrefix_should_trim_and_remove_trailing_slash() {
        UploadProperties p1 = new UploadProperties();
        p1.setUrlPrefix(" /x/ ");
        assertEquals("/x", p1.normalizedUrlPrefix());

        UploadProperties p2 = new UploadProperties();
        p2.setUrlPrefix("/y");
        assertEquals("/y", p2.normalizedUrlPrefix());
    }
}

