package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import jakarta.persistence.Column;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostAttachmentsDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void entity_shouldUse512LengthForFileNameColumn() throws Exception {
        Column col = PostAttachmentsEntity.class.getDeclaredField("fileName").getAnnotation(Column.class);
        assertThat(col).isNotNull();
        assertThat(col.length()).isEqualTo(512);
    }

    @Test
    void createDto_shouldAllow512CharFileName() {
        PostAttachmentsCreateDTO dto = new PostAttachmentsCreateDTO();
        dto.setPostId(1L);
        dto.setUrl("https://example.com/uploads/1.png");
        dto.setFileName("a".repeat(512));
        dto.setMimeType("image/png");
        dto.setSizeBytes(1L);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void createDto_shouldReject513CharFileName() {
        PostAttachmentsCreateDTO dto = new PostAttachmentsCreateDTO();
        dto.setPostId(1L);
        dto.setUrl("https://example.com/uploads/1.png");
        dto.setFileName("a".repeat(513));
        dto.setMimeType("image/png");
        dto.setSizeBytes(1L);

        assertThat(validator.validate(dto))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("fileName");
    }
}

