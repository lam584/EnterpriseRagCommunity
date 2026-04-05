package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.function.UnaryOperator;

public final class PostSuggestionConfigSupport {

    private PostSuggestionConfigSupport() {
    }

    public static Pageable buildHistoryPageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return PageRequest.of(safePage, safeSize);
    }

    public static <T extends PostSuggestionGenConfigEntity> T saveUpdatedConfig(T entity,
                                                                                Long actorUserId,
                                                                                UnaryOperator<T> saver) {
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(actorUserId);
        return saver.apply(entity);
    }
}
