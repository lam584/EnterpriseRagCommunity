package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared helper for moderation runners to load plain text for a queue item.
 */
@Component
@RequiredArgsConstructor
public class ModerationContentTextLoader {

    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;

    public String load(ModerationQueueEntity q) {
        if (q == null || q.getContentType() == null || q.getContentId() == null) return null;
        try {
            return switch (q.getContentType()) {
                case POST -> {
                    var post = postsRepository.findById(q.getContentId()).orElse(null);
                    if (post == null) yield null;
                    String title = post.getTitle() == null ? "" : post.getTitle();
                    String content = post.getContent() == null ? "" : post.getContent();
                    yield (title + "\n" + content).trim();
                }
                case COMMENT -> {
                    var c = commentsRepository.findById(q.getContentId()).orElse(null);
                    if (c == null) yield null;
                    yield c.getContent();
                }
            };
        } catch (Exception e) {
            return null;
        }
    }
}
