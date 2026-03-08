package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.UsersQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.response.AdminUserTotpStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.TotpSecretsRepository;
import com.example.EnterpriseRagCommunity.service.access.UsersService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUsersTotpControllerTest {

    @Test
    void reset_should_reject_invalid_userId() {
        UsersService usersService = mock(UsersService.class);
        TotpSecretsRepository repo = mock(TotpSecretsRepository.class);
        AdminUsersTotpController controller = new AdminUsersTotpController(usersService, repo);

        assertThatThrownBy(() -> controller.reset(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is required");
    }

    @Test
    void reset_should_skip_save_when_no_rows() {
        UsersService usersService = mock(UsersService.class);
        TotpSecretsRepository repo = mock(TotpSecretsRepository.class);
        when(repo.findByUserId(1L)).thenReturn(List.of());

        AdminUsersTotpController controller = new AdminUsersTotpController(usersService, repo);
        controller.reset(1L);

        verify(repo, never()).saveAll(any());
    }

    @Test
    void reset_should_disable_all_rows_and_save() {
        UsersService usersService = mock(UsersService.class);
        TotpSecretsRepository repo = mock(TotpSecretsRepository.class);

        TotpSecretsEntity r1 = new TotpSecretsEntity();
        r1.setUserId(1L);
        r1.setEnabled(true);
        TotpSecretsEntity r2 = new TotpSecretsEntity();
        r2.setUserId(1L);
        r2.setEnabled(true);
        List<TotpSecretsEntity> rows = new ArrayList<>(List.of(r1, r2));
        when(repo.findByUserId(1L)).thenReturn(rows);

        AdminUsersTotpController controller = new AdminUsersTotpController(usersService, repo);
        controller.reset(1L);

        assertThat(rows).allMatch(e -> Boolean.FALSE.equals(e.getEnabled()));
        verify(repo).saveAll(eq(rows));
    }

    @Test
    void query_should_map_enabled_row_when_present() {
        UsersService usersService = mock(UsersService.class);
        TotpSecretsRepository repo = mock(TotpSecretsRepository.class);
        AdminUsersTotpController controller = new AdminUsersTotpController(usersService, repo);

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("u@example.com");
        u.setUsername("u");

        when(usersService.query(any())).thenReturn(new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1));

        TotpSecretsEntity enabled = new TotpSecretsEntity();
        enabled.setUserId(1L);
        enabled.setEnabled(true);
        enabled.setCreatedAt(LocalDateTime.now());
        enabled.setVerifiedAt(LocalDateTime.now());
        enabled.setAlgorithm("SHA1");
        enabled.setDigits(6);
        enabled.setPeriodSeconds(30);
        enabled.setSkew(1);

        when(repo.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(enabled));

        var res = controller.query(new UsersQueryDTO()).getBody();
        assertThat(res).isNotNull();
        AdminUserTotpStatusDTO dto = res.getContent().get(0);
        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getAlgorithm()).isEqualTo("SHA1");
    }

    @Test
    void query_should_fallback_to_latest_row_and_handle_null_enabled() {
        UsersService usersService = mock(UsersService.class);
        TotpSecretsRepository repo = mock(TotpSecretsRepository.class);
        AdminUsersTotpController controller = new AdminUsersTotpController(usersService, repo);

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("u@example.com");
        u.setUsername("u");

        when(usersService.query(any())).thenReturn(new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1));

        when(repo.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        TotpSecretsEntity latest = new TotpSecretsEntity();
        latest.setUserId(1L);
        latest.setEnabled(null);
        latest.setCreatedAt(LocalDateTime.now());
        when(repo.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(latest));

        var res = controller.query(new UsersQueryDTO()).getBody();
        assertThat(res).isNotNull();
        AdminUserTotpStatusDTO dto = res.getContent().get(0);
        assertThat(dto.getEnabled()).isFalse();
    }

    @Test
    void query_should_return_disabled_when_no_rows() {
        UsersService usersService = mock(UsersService.class);
        TotpSecretsRepository repo = mock(TotpSecretsRepository.class);
        AdminUsersTotpController controller = new AdminUsersTotpController(usersService, repo);

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("u@example.com");
        u.setUsername("u");

        when(usersService.query(any())).thenReturn(new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1));
        when(repo.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(repo.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        var res = controller.query(new UsersQueryDTO()).getBody();
        assertThat(res).isNotNull();
        AdminUserTotpStatusDTO dto = res.getContent().get(0);
        assertThat(dto.getEnabled()).isFalse();
    }
}
