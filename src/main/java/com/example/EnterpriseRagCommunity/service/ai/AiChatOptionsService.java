package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatModelOptionDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatOptionsDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatProviderOptionDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProviderDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProvidersConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiChatOptionsService {
    private static final String ENV_DEFAULT = "default";

    private final AiProvidersConfigService aiProvidersConfigService;
    private final LlmModelRepository llmModelRepository;

    @Transactional(readOnly = true)
    public AiChatOptionsDTO getOptions() {
        AiProvidersConfigDTO cfg = aiProvidersConfigService.getAdminConfig();

        String activeProviderId = toNonBlank(cfg == null ? null : cfg.getActiveProviderId());
        List<AiProviderDTO> providers = (cfg == null || cfg.getProviders() == null) ? List.of() : cfg.getProviders();

        List<AiChatProviderOptionDTO> outProviders = new ArrayList<>();
        for (AiProviderDTO p : providers) {
            if (p == null) continue;
            if (Boolean.FALSE.equals(p.getEnabled())) continue;
            String pid = toNonBlank(p.getId());
            if (pid == null) continue;

            AiChatProviderOptionDTO dto = new AiChatProviderOptionDTO();
            dto.setId(pid);
            dto.setName(toNonBlank(p.getName()));
            dto.setDefaultChatModel(toNonBlank(p.getDefaultChatModel()));

            List<AiChatModelOptionDTO> models = loadChatModels(pid);
            if ((models == null || models.isEmpty()) && toNonBlank(dto.getDefaultChatModel()) != null) {
                AiChatModelOptionDTO fallback = new AiChatModelOptionDTO();
                fallback.setName(dto.getDefaultChatModel());
                fallback.setIsDefault(true);
                models = List.of(fallback);
            }
            dto.setChatModels(models == null ? List.of() : models);
            outProviders.add(dto);
        }

        if (activeProviderId == null && !outProviders.isEmpty()) {
            activeProviderId = toNonBlank(outProviders.get(0).getId());
        } else if (activeProviderId != null) {
            boolean exists = false;
            for (AiChatProviderOptionDTO p : outProviders) {
                if (p != null && activeProviderId.equals(toNonBlank(p.getId()))) {
                    exists = true;
                    break;
                }
            }
            if (!exists && !outProviders.isEmpty()) activeProviderId = toNonBlank(outProviders.get(0).getId());
        }

        AiChatOptionsDTO out = new AiChatOptionsDTO();
        out.setActiveProviderId(activeProviderId);
        out.setProviders(outProviders);
        return out;
    }

    private List<AiChatModelOptionDTO> loadChatModels(String providerId) {
        String pid = toNonBlank(providerId);
        if (pid == null) return List.of();
        List<LlmModelEntity> rows = llmModelRepository.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(ENV_DEFAULT, pid);
        if (rows == null || rows.isEmpty()) return List.of();

        List<AiChatModelOptionDTO> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LlmModelEntity e : rows) {
            if (e == null) continue;
            if (Boolean.FALSE.equals(e.getEnabled())) continue;
            String purpose = toNonBlank(e.getPurpose());
            if (purpose == null) continue;
            String up = purpose.trim().toUpperCase(Locale.ROOT);
            if (!"MULTIMODAL_CHAT".equals(up) && !"TEXT_CHAT".equals(up) && !"IMAGE_CHAT".equals(up) && !"CHAT".equals(up)) continue;
            String name = toNonBlank(e.getModelName());
            if (name == null) continue;
            if (seen.contains(name)) continue;
            seen.add(name);
            AiChatModelOptionDTO m = new AiChatModelOptionDTO();
            m.setName(name);
            m.setIsDefault(Boolean.TRUE.equals(e.getIsDefault()));
            out.add(m);
        }
        return out;
    }

    private static String toNonBlank(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isBlank() ? null : s;
    }
}
