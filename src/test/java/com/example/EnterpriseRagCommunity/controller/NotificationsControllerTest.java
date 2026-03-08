package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.NotificationsEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationsService notificationsService;

    @MockitoBean
    private AdministratorService administratorService;

    @MockitoBean
    private AuditLogWriter auditLogWriter;

    private static UsersEntity userEntity(long id, String email) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        u.setEmail(email);
        u.setIsDeleted(false);
        return u;
    }

    @Test
    void listMyNotifications_shouldClampPageAndPageSize() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(notificationsService.listMyNotifications(any(), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/notifications")
                        .param("page", "0")
                        .param("pageSize", "999"))
                .andExpect(status().isOk());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(200);
    }

    @Test
    void unreadCount_shouldReturn200() throws Exception {
        when(notificationsService.countMyUnread()).thenReturn(7L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7));
    }

    @Test
    void markRead_shouldWriteAudit_whenUserIdResolved() throws Exception {
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.of(userEntity(1L, "u@example.invalid")));

        NotificationsEntity e = new NotificationsEntity();
        e.setId(10L);
        when(notificationsService.markMyNotificationRead(10L)).thenReturn(e);

        mockMvc.perform(patch("/api/notifications/10/read").with(user("u@example.invalid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(auditLogWriter).write(eq(1L), eq("u@example.invalid"), eq("NOTIFICATION_MARK_READ"), eq("NOTIFICATION"), eq(10L), any(), any(), any(), any());
    }

    @Test
    void markRead_shouldNotWriteAudit_whenUserIdNotResolved() throws Exception {
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.empty());

        NotificationsEntity e = new NotificationsEntity();
        e.setId(10L);
        when(notificationsService.markMyNotificationRead(10L)).thenReturn(e);

        mockMvc.perform(patch("/api/notifications/10/read").with(user("u@example.invalid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void markRead_shouldRethrow_andIncludeTruncatedMessageInAuditDetails() throws Exception {
        String longMsg = "x".repeat(300);
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.of(userEntity(1L, "u@example.invalid")));
        when(notificationsService.markMyNotificationRead(10L)).thenThrow(new RuntimeException(longMsg));

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);

        mockMvc.perform(patch("/api/notifications/10/read").with(user("u@example.invalid")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("系统处理请求时发生错误"));

        verify(auditLogWriter).write(eq(1L), eq("u@example.invalid"), eq("NOTIFICATION_MARK_READ"), eq("NOTIFICATION"), eq(10L), any(), any(), any(), detailsCaptor.capture());
        Object msg = detailsCaptor.getValue().get("message");
        assertThat(msg).isInstanceOf(String.class);
        assertThat(((String) msg).length()).isEqualTo(256);
    }

    @Test
    void markReadBatch_shouldReturn400_whenIdsInvalid() throws Exception {
        mockMvc.perform(patch("/api/notifications/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.updated").value(0));
    }

    @Test
    void markReadBatch_shouldFilterIdsAndReturn200() throws Exception {
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.of(userEntity(1L, "u@example.invalid")));
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        when(notificationsService.markMyNotificationsRead(idsCaptor.capture())).thenReturn(2);

        mockMvc.perform(patch("/api/notifications/read")
                        .with(user("u@example.invalid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,\"x\",2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    void markReadBatch_shouldRethrow_whenServiceThrows() throws Exception {
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.of(userEntity(1L, "u@example.invalid")));
        when(notificationsService.markMyNotificationsRead(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(patch("/api/notifications/read")
                        .with(user("u@example.invalid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("系统处理请求时发生错误"));
    }

    @Test
    void delete_shouldReturn204_whenSuccess() throws Exception {
        mockMvc.perform(delete("/api/notifications/10").with(user("u@example.invalid")))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_shouldRethrow_whenServiceThrows() throws Exception {
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.of(userEntity(1L, "u@example.invalid")));
        doThrow(new RuntimeException("boom")).when(notificationsService).deleteMyNotification(10L);

        mockMvc.perform(delete("/api/notifications/10").with(user("u@example.invalid")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("系统处理请求时发生错误"));
    }
}
