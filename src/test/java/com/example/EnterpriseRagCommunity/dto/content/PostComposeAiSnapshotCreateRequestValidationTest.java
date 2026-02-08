package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostComposeAiSnapshotCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAllowBlankBeforeContent() {
        PostComposeAiSnapshotCreateRequest req = new PostComposeAiSnapshotCreateRequest();
        req.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
        req.setDraftId(1L);
        req.setBeforeTitle("");
        req.setBeforeContent("");
        req.setBeforeBoardId(1L);

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void shouldRejectNullBeforeContent() {
        PostComposeAiSnapshotCreateRequest req = new PostComposeAiSnapshotCreateRequest();
        req.setTargetType(PostComposeAiSnapshotTargetType.DRAFT);
        req.setDraftId(1L);
        req.setBeforeTitle("");
        req.setBeforeContent(null);
        req.setBeforeBoardId(1L);

        assertThat(validator.validate(req))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("beforeContent");
    }
}

