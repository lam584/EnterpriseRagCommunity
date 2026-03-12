package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryRegenerateRequestDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostSummaryService;
import com.example.EnterpriseRagCommunity.service.ai.PostSummaryGenConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPostSummaryControllerUnitTest {

    @Test
    void upsertConfig_shouldPassNullActor_whenPrincipalIsNull() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdminPostSummaryController controller = new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);

        PostSummaryGenConfigDTO payload = new PostSummaryGenConfigDTO();
        PostSummaryGenConfigDTO result = new PostSummaryGenConfigDTO();
        when(configService.upsertAdminConfig(eq(payload), eq(null), eq(null))).thenReturn(result);

        controller.upsertConfig(payload, null);

        verify(configService).upsertAdminConfig(eq(payload), eq(null), eq(null));
        verify(administratorService, never()).findByUsername("admin");
    }

    @Test
    void upsertConfig_shouldResolveActorUserId_whenPrincipalUserExists() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdminPostSummaryController controller = new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);

        Principal principal = () -> "admin";
        UsersEntity user = new UsersEntity();
        user.setId(21L);
        when(administratorService.findByUsername("admin")).thenReturn(Optional.of(user));

        PostSummaryGenConfigDTO payload = new PostSummaryGenConfigDTO();
        PostSummaryGenConfigDTO result = new PostSummaryGenConfigDTO();
        when(configService.upsertAdminConfig(eq(payload), eq(21L), eq("admin"))).thenReturn(result);

        controller.upsertConfig(payload, principal);

        verify(administratorService).findByUsername("admin");
        verify(configService).upsertAdminConfig(eq(payload), eq(21L), eq("admin"));
    }

    @Test
    void upsertConfig_shouldUseNullActorUserId_whenPrincipalUserMissing() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdminPostSummaryController controller = new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);

        Principal principal = () -> "admin";
        when(administratorService.findByUsername("admin")).thenReturn(Optional.empty());

        PostSummaryGenConfigDTO payload = new PostSummaryGenConfigDTO();
        PostSummaryGenConfigDTO result = new PostSummaryGenConfigDTO();
        when(configService.upsertAdminConfig(eq(payload), eq(null), eq("admin"))).thenReturn(result);

        controller.upsertConfig(payload, principal);

        verify(administratorService).findByUsername("admin");
        verify(configService).upsertAdminConfig(eq(payload), eq(null), eq("admin"));
    }

    @Test
    void regenerate_shouldThrowIllegalArgument_whenPayloadIsNull() {
        AdminPostSummaryController controller = newController();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> controller.regenerate(null, null));
        assertEquals("postId 不能为空", ex.getMessage());
    }

    @Test
    void regenerate_shouldThrowIllegalArgument_whenPostIdNotPositive() {
        AdminPostSummaryController controller = newController();
        PostSummaryRegenerateRequestDTO payload = new PostSummaryRegenerateRequestDTO();
        payload.setPostId(0L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> controller.regenerate(payload, null));
        assertEquals("postId 不能为空", ex.getMessage());
    }

    @Test
    void regenerate_shouldThrowIllegalState_whenFeatureDisabled() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdminPostSummaryController controller = new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);

        PostSummaryRegenerateRequestDTO payload = new PostSummaryRegenerateRequestDTO();
        payload.setPostId(1L);
        PostSummaryGenConfigEntity config = new PostSummaryGenConfigEntity();
        config.setEnabled(false);
        when(configService.getConfigEntityOrDefault()).thenReturn(config);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> controller.regenerate(payload, null));
        assertEquals("帖子摘要功能未启用，无法重新生成", ex.getMessage());
    }

    @Test
    void regenerate_shouldThrowNotFound_whenPostDoesNotExist() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdminPostSummaryController controller = new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);

        PostSummaryRegenerateRequestDTO payload = new PostSummaryRegenerateRequestDTO();
        payload.setPostId(2L);
        PostSummaryGenConfigEntity config = new PostSummaryGenConfigEntity();
        config.setEnabled(true);
        when(configService.getConfigEntityOrDefault()).thenReturn(config);
        when(postsRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> controller.regenerate(payload, null));
        assertEquals("帖子不存在或已删除", ex.getMessage());
    }

    @Test
    void regenerate_shouldAccept_whenPrincipalIsNull() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdminPostSummaryController controller = new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);

        PostSummaryRegenerateRequestDTO payload = new PostSummaryRegenerateRequestDTO();
        payload.setPostId(3L);
        PostSummaryGenConfigEntity config = new PostSummaryGenConfigEntity();
        config.setEnabled(true);
        when(configService.getConfigEntityOrDefault()).thenReturn(config);
        when(postsRepository.findByIdAndIsDeletedFalse(3L)).thenReturn(Optional.of(new PostsEntity()));

        ResponseEntity<Map<String, String>> response = controller.regenerate(payload, null);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("已提交重新生成任务", response.getBody().get("message"));
        verify(aiPostSummaryService).generateForPostIdAsync(3L, null);
        verify(administratorService, never()).findByUsername("admin");
    }

    @Test
    void regenerate_shouldAcceptWithResolvedActor_whenPrincipalUserExists() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdminPostSummaryController controller = new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);

        PostSummaryRegenerateRequestDTO payload = new PostSummaryRegenerateRequestDTO();
        payload.setPostId(4L);
        PostSummaryGenConfigEntity config = new PostSummaryGenConfigEntity();
        config.setEnabled(true);
        when(configService.getConfigEntityOrDefault()).thenReturn(config);
        when(postsRepository.findByIdAndIsDeletedFalse(4L)).thenReturn(Optional.of(new PostsEntity()));
        UsersEntity user = new UsersEntity();
        user.setId(31L);
        when(administratorService.findByUsername("admin")).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, String>> response = controller.regenerate(payload, (Principal) () -> "admin");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(aiPostSummaryService).generateForPostIdAsync(4L, 31L);
    }

    @Test
    void regenerate_shouldAcceptWithNullActorUserId_whenPrincipalUserMissing() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdminPostSummaryController controller = new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);

        PostSummaryRegenerateRequestDTO payload = new PostSummaryRegenerateRequestDTO();
        payload.setPostId(5L);
        PostSummaryGenConfigEntity config = new PostSummaryGenConfigEntity();
        config.setEnabled(true);
        when(configService.getConfigEntityOrDefault()).thenReturn(config);
        when(postsRepository.findByIdAndIsDeletedFalse(5L)).thenReturn(Optional.of(new PostsEntity()));
        when(administratorService.findByUsername("admin")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = controller.regenerate(payload, (Principal) () -> "admin");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(aiPostSummaryService).generateForPostIdAsync(5L, null);
    }

    private AdminPostSummaryController newController() {
        PostSummaryGenConfigService configService = mock(PostSummaryGenConfigService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        return new AdminPostSummaryController(configService, administratorService, aiPostSummaryService, postsRepository);
    }
}
