package com.example.EnterpriseRagCommunity.dto.content;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostAttachmentsDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createDto_shouldPassWithRequiredFields() {
        PostAttachmentsCreateDTO dto = new PostAttachmentsCreateDTO();
        dto.setPostId(1L);
        dto.setFileAssetId(100L);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void createDto_shouldRejectNullPostId() {
        PostAttachmentsCreateDTO dto = new PostAttachmentsCreateDTO();
        dto.setFileAssetId(100L);

        assertThat(validator.validate(dto))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("postId");
    }

    @Test
    void createDto_shouldRejectNullFileAssetId() {
        PostAttachmentsCreateDTO dto = new PostAttachmentsCreateDTO();
        dto.setPostId(1L);

        assertThat(validator.validate(dto))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("fileAssetId");
    }
}

