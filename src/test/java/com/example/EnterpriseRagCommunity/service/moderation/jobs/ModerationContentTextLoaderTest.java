package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationContentTextLoaderTest {

    @Test
    void load_shouldReturnNull_whenQueueIsNull() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);

        assertNull(loader.load(null));
        verify(postsRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(commentsRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(usersRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void load_shouldReturnNull_whenContentTypeIsNull() {
        ModerationContentTextLoader loader = newLoader();
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setContentType(null);
        q.setContentId(1L);

        assertNull(loader.load(q));
    }

    @Test
    void load_shouldReturnNull_whenContentIdIsNull() {
        ModerationContentTextLoader loader = newLoader();
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setContentType(ContentType.POST);
        q.setContentId(null);

        assertNull(loader.load(q));
    }

    @Test
    void load_shouldReturnNull_whenPostNotFound() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.POST, 11L, ModerationCaseType.CONTENT);

        when(postsRepository.findById(11L)).thenReturn(Optional.empty());

        assertNull(loader.load(q));
    }

    @Test
    void load_shouldJoinTitleAndContent_forPost() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.POST, 12L, ModerationCaseType.CONTENT);
        PostsEntity post = new PostsEntity();
        post.setTitle("标题");
        post.setContent("正文");

        when(postsRepository.findById(12L)).thenReturn(Optional.of(post));

        assertEquals("标题\n正文", loader.load(q));
    }

    @Test
    void load_shouldReturnEmptyString_whenPostTitleAndContentAreNull() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.POST, 13L, ModerationCaseType.CONTENT);
        PostsEntity post = new PostsEntity();
        post.setTitle(null);
        post.setContent(null);

        when(postsRepository.findById(13L)).thenReturn(Optional.of(post));

        assertEquals("", loader.load(q));
    }

    @Test
    void load_shouldReturnNull_whenCommentNotFound() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.COMMENT, 21L, ModerationCaseType.CONTENT);

        when(commentsRepository.findById(21L)).thenReturn(Optional.empty());

        assertNull(loader.load(q));
    }

    @Test
    void load_shouldReturnCommentContent_forComment() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.COMMENT, 22L, ModerationCaseType.CONTENT);
        CommentsEntity c = new CommentsEntity();
        c.setContent("评论文本");

        when(commentsRepository.findById(22L)).thenReturn(Optional.of(c));

        assertEquals("评论文本", loader.load(q));
    }

    @Test
    void load_shouldReturnNull_whenProfileUserNotFound() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.PROFILE, 31L, ModerationCaseType.CONTENT);

        when(usersRepository.findById(31L)).thenReturn(Optional.empty());

        assertNull(loader.load(q));
    }

    @Test
    void load_shouldUseProfilePending_whenCaseTypeIsContentAndPendingExists() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.PROFILE, 32L, ModerationCaseType.CONTENT);
        UsersEntity u = new UsersEntity();
        u.setUsername("fallback-user");

        Map<Object, Object> pending = new LinkedHashMap<>();
        pending.put("username", "pending-user");
        pending.put("bio", "pending-bio");
        pending.put("location", "CN");
        pending.put("website", "https://pending.example");
        pending.put(null, "ignored");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username", "profile-user");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("profilePending", pending);
        metadata.put("profile", profile);
        u.setMetadata(metadata);

        when(usersRepository.findById(32L)).thenReturn(Optional.of(u));

        assertEquals("username: pending-user\nbio: pending-bio\nlocation: CN\nwebsite: https://pending.example", loader.load(q));
    }

    @Test
    void load_shouldUseProfileAndFallbackUsername_whenProfileUsernameBlank() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.PROFILE, 33L, ModerationCaseType.REPORT);
        UsersEntity u = new UsersEntity();
        u.setUsername("fallback-user");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username", "   ");
        profile.put("bio", null);
        profile.put("location", "SZ");
        profile.put("website", 12345);

        u.setMetadata(Map.of("profile", profile));

        when(usersRepository.findById(33L)).thenReturn(Optional.of(u));

        assertEquals("username: fallback-user\nbio: \nlocation: SZ\nwebsite: 12345", loader.load(q));
    }

    @Test
    void load_shouldBuildEmptyProfileFields_whenMetadataMissingAndUsernameNull() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.PROFILE, 34L, ModerationCaseType.CONTENT);
        UsersEntity u = new UsersEntity();
        u.setUsername(null);
        u.setMetadata(Map.of("profile", "not-a-map"));

        when(usersRepository.findById(34L)).thenReturn(Optional.of(u));

        assertEquals("username: \nbio: \nlocation: \nwebsite:", loader.load(q));
    }

    @Test
    void load_shouldFallbackToUsername_whenProfileMetadataIsNull() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.PROFILE, 35L, ModerationCaseType.CONTENT);
        UsersEntity u = new UsersEntity();
        u.setUsername("fallback-name");
        u.setMetadata(null);

        when(usersRepository.findById(35L)).thenReturn(Optional.of(u));

        assertEquals("username: fallback-name\nbio: \nlocation: \nwebsite:", loader.load(q));
    }

    @Test
    void load_shouldHandleProfileValueWhoseToStringReturnsNull() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.PROFILE, 36L, ModerationCaseType.REPORT);
        UsersEntity u = new UsersEntity();
        u.setUsername("u36");

        Object nullToString = new Object() {
            @Override
            public String toString() {
                return null;
            }
        };
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username", "u36");
        profile.put("bio", nullToString);
        profile.put("location", "BJ");
        profile.put("website", "https://example.org");
        u.setMetadata(Map.of("profile", profile));

        when(usersRepository.findById(36L)).thenReturn(Optional.of(u));

        assertEquals("username: u36\nbio: \nlocation: BJ\nwebsite: https://example.org", loader.load(q));
    }

    @Test
    void load_shouldReturnNull_whenRepositoryThrowsException() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationContentTextLoader loader = new ModerationContentTextLoader(postsRepository, commentsRepository, usersRepository);
        ModerationQueueEntity q = queue(ContentType.POST, 99L, ModerationCaseType.CONTENT);

        when(postsRepository.findById(99L)).thenThrow(new RuntimeException("boom"));

        assertNull(loader.load(q));
    }

    private static ModerationContentTextLoader newLoader() {
        return new ModerationContentTextLoader(mock(PostsRepository.class), mock(CommentsRepository.class), mock(UsersRepository.class));
    }

    private static ModerationQueueEntity queue(ContentType type, Long contentId, ModerationCaseType caseType) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setContentType(type);
        q.setContentId(contentId);
        q.setCaseType(caseType);
        return q;
    }
}
