package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helper for moderation runners to load plain text for a queue item.
 */
@Component
@RequiredArgsConstructor
public class ModerationContentTextLoader {

    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final UsersRepository usersRepository;

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
                case PROFILE -> {
                    var u = usersRepository.findById(q.getContentId()).orElse(null);
                    if (u == null) yield null;
                    Map<String, Object> meta = u.getMetadata();
                    String profileKey = (q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.CONTENT && meta != null && meta.get("profilePending") instanceof Map<?, ?>)
                            ? "profilePending"
                            : "profile";
                    Map<String, Object> profile = null;
                    if (meta != null && meta.get(profileKey) instanceof Map<?, ?> mm) {
                        profile = new LinkedHashMap<>();
                        for (var e : mm.entrySet()) {
                            if (e.getKey() != null) profile.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                    String username = profile == null ? "" : emptyToEmpty(profile.get("username"));
                    if (username.trim().isEmpty()) username = u.getUsername() == null ? "" : u.getUsername();
                    String bio = profile == null ? "" : emptyToEmpty(profile.get("bio"));
                    String location = profile == null ? "" : emptyToEmpty(profile.get("location"));
                    String website = profile == null ? "" : emptyToEmpty(profile.get("website"));
                    yield ("username: " + username
                            + "\nbio: " + bio
                            + "\nlocation: " + location
                            + "\nwebsite: " + website).trim();
                }
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToEmpty(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        return s == null ? "" : s;
    }
}
