package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostDraftsDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createDto_shouldAllowBlankTitleAndContent() {
        PostDraftsCreateDTO dto = new PostDraftsCreateDTO();
        dto.setBoardId(1L);
        dto.setTitle("");
        dto.setContent("");
        dto.setContentFormat(ContentFormat.MARKDOWN);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void updateDto_shouldAllowNullTitleAndContent() {
        PostDraftsUpdateDTO dto = new PostDraftsUpdateDTO();
        dto.setBoardId(1L);
        dto.setTitle(null);
        dto.setContent(null);
        dto.setContentFormat(ContentFormat.MARKDOWN);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void createDto_shouldRejectMissingBoardIdAndContentFormat() {
        PostDraftsCreateDTO dto = new PostDraftsCreateDTO();
        dto.setTitle("");
        dto.setContent("hello");

        assertThat(validator.validate(dto))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("boardId", "contentFormat");
    }

    @Test
    void createDto_shouldRejectTooLongTitle() {
        PostDraftsCreateDTO dto = new PostDraftsCreateDTO();
        dto.setBoardId(1L);
        dto.setTitle("a".repeat(192));
        dto.setContent("hello");
        dto.setContentFormat(ContentFormat.MARKDOWN);

        assertThat(validator.validate(dto))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("title");
    }
}

