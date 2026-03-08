package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationPolicyConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminModerationPolicyService {

    private final ModerationPolicyConfigRepository repository;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @Transactional(readOnly = true)
    public ModerationPolicyConfigDTO getConfig(ContentType contentType) {
        if (contentType == null) throw new IllegalArgumentException("contentType is required");
        ModerationPolicyConfigEntity cfg = repository.findByContentType(contentType)
                .orElseThrow(() -> new IllegalStateException("moderation_policy_config not initialized for contentType=" + contentType));
        return toDto(cfg, null);
    }

    @Transactional
    public ModerationPolicyConfigDTO upsert(ModerationPolicyConfigDTO payload, Long actorUserId, String actorUsername) {
        if (payload == null) throw new IllegalArgumentException("body is required");
        if (payload.getContentType() == null) throw new IllegalArgumentException("contentType is required");
        if (payload.getPolicyVersion() == null || payload.getPolicyVersion().isBlank()) throw new IllegalArgumentException("policyVersion is required");
        if (payload.getConfig() == null) throw new IllegalArgumentException("config is required");

        ModerationPolicyConfigDTO beforeDto = getConfig(payload.getContentType());

        ModerationPolicyConfigEntity cfg = repository.findByContentType(payload.getContentType())
                .orElseGet(ModerationPolicyConfigEntity::new);

        cfg.setContentType(payload.getContentType());
        cfg.setPolicyVersion(payload.getPolicyVersion().trim());
        cfg.setConfig(sanitizeConfig(payload.getConfig()));
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(actorUserId);

        cfg = repository.save(cfg);
        ModerationPolicyConfigDTO afterDto = toDto(cfg, actorUsername);
        auditLogWriter.write(
                actorUserId,
                actorUsername,
                "CONFIG_CHANGE",
                "MODERATION_POLICY_CONFIG",
                cfg.getId(),
                AuditResult.SUCCESS,
                "更新审核策略配置",
                null,
                auditDiffBuilder.build(beforeDto, afterDto)
        );
        return afterDto;
    }

    private static ModerationPolicyConfigDTO toDto(ModerationPolicyConfigEntity cfg, String updatedByUsername) {
        ModerationPolicyConfigDTO dto = new ModerationPolicyConfigDTO();
        dto.setId(cfg.getId());
        dto.setVersion(cfg.getVersion());
        dto.setContentType(cfg.getContentType());
        dto.setPolicyVersion(cfg.getPolicyVersion());
        dto.setConfig(sanitizeConfig(cfg.getConfig()));
        dto.setUpdatedAt(cfg.getUpdatedAt());
        dto.setUpdatedBy(updatedByUsername);
        return dto;
    }

    private static Map<String, Object> sanitizeConfig(Map<String, Object> in) {
        if (in == null || in.isEmpty()) return Map.of();
        return Map.copyOf(in);
    }
}
