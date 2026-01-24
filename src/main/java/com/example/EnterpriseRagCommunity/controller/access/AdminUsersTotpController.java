package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.UsersQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.response.AdminUserTotpStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.TotpSecretsRepository;
import com.example.EnterpriseRagCommunity.service.access.UsersService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/users/totp")
@RequiredArgsConstructor
@Api(tags = "后台-用户 TOTP 状态")
public class AdminUsersTotpController {
    private final UsersService usersService;
    private final TotpSecretsRepository totpSecretsRepository;

    @PostMapping("/query")
    @ApiOperation("分页查询用户 TOTP 状态")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<Page<AdminUserTotpStatusDTO>> query(@RequestBody UsersQueryDTO queryDTO) {
        Page<UsersEntity> users = usersService.query(queryDTO);
        Page<AdminUserTotpStatusDTO> dtoPage = users.map(this::toDto);
        return ResponseEntity.ok(dtoPage);
    }

    @PostMapping("/{userId}/reset")
    @ApiOperation("重置用户 TOTP（禁用所有记录）")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<Void> reset(@PathVariable("userId") Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId is required");
        List<TotpSecretsEntity> rows = totpSecretsRepository.findByUserId(userId);
        for (TotpSecretsEntity e : rows) {
            e.setEnabled(false);
        }
        if (!rows.isEmpty()) totpSecretsRepository.saveAll(rows);
        return ResponseEntity.ok().build();
    }

    private AdminUserTotpStatusDTO toDto(UsersEntity u) {
        AdminUserTotpStatusDTO dto = new AdminUserTotpStatusDTO();
        dto.setUserId(u.getId());
        dto.setEmail(u.getEmail());
        dto.setUsername(u.getUsername());

        Optional<TotpSecretsEntity> enabled = totpSecretsRepository.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(u.getId());
        TotpSecretsEntity row = enabled.orElseGet(() -> totpSecretsRepository.findTopByUserIdOrderByCreatedAtDesc(u.getId()).orElse(null));
        if (row == null) {
            dto.setEnabled(false);
            return dto;
        }

        dto.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        dto.setVerifiedAt(row.getVerifiedAt());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setAlgorithm(row.getAlgorithm());
        dto.setDigits(row.getDigits());
        dto.setPeriodSeconds(row.getPeriodSeconds());
        dto.setSkew(row.getSkew());
        return dto;
    }
}

