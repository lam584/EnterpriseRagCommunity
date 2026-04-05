package com.example.EnterpriseRagCommunity.service.content.impl;

import java.util.function.Consumer;

final class PostContentFieldSupport {

    private PostContentFieldSupport() {
    }

    static <F> void applyCommonFields(
            Long id,
            Long tenantId,
            Long boardId,
            Long authorId,
            String title,
            String content,
            F contentFormat,
            Consumer<Long> idSetter,
            Consumer<Long> tenantIdSetter,
            Consumer<Long> boardIdSetter,
            Consumer<Long> authorIdSetter,
            Consumer<String> titleSetter,
            Consumer<String> contentSetter,
            Consumer<F> contentFormatSetter
    ) {
        idSetter.accept(id);
        tenantIdSetter.accept(tenantId);
        boardIdSetter.accept(boardId);
        authorIdSetter.accept(authorId);
        titleSetter.accept(title);
        contentSetter.accept(content);
        contentFormatSetter.accept(contentFormat);
    }
}
