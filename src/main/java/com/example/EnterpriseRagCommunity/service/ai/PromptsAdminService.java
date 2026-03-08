package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromptsAdminService {
    private final PromptsRepository promptsRepository;

    @Transactional(readOnly = true)
    public PromptBatchResponse batchGetByCodes(List<String> codes) {
        List<String> normalized = normalizeCodes(codes);
        List<PromptsEntity> found = normalized.isEmpty() ? List.of() : promptsRepository.findByPromptCodeIn(normalized);

        Map<String, PromptsEntity> byCode = found.stream()
                .filter(p -> p.getPromptCode() != null && !p.getPromptCode().isBlank())
                .collect(Collectors.toMap(p -> p.getPromptCode().trim(), p -> p, (a, b) -> a));

        List<PromptContentDTO> prompts = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String code : normalized) {
            PromptsEntity p = byCode.get(code);
            if (p == null) {
                missing.add(code);
                continue;
            }
            prompts.add(toDTO(p));
        }

        PromptBatchResponse resp = new PromptBatchResponse();
        resp.setPrompts(prompts);
        resp.setMissingCodes(missing);
        return resp;
    }

    @Transactional
    public PromptContentDTO updateContent(String promptCode, PromptContentUpdateRequest req, Long updatedBy) {
        String code = normalizeCode(promptCode);
        PromptsEntity entity = promptsRepository.findByPromptCode(code)
                .orElseThrow(() -> new java.util.NoSuchElementException("prompt not found: " + code));

        if (req != null) {
            if (req.getName() != null) entity.setName(req.getName());
            if (req.getSystemPrompt() != null) entity.setSystemPrompt(req.getSystemPrompt());
            if (req.getUserPromptTemplate() != null) entity.setUserPromptTemplate(req.getUserPromptTemplate());
            if (req.getTemperature() != null) entity.setTemperature(req.getTemperature());
            if (req.getTopP() != null) entity.setTopP(req.getTopP());
            if (req.getMaxTokens() != null) entity.setMaxTokens(req.getMaxTokens());
            if (req.getEnableDeepThinking() != null) entity.setEnableDeepThinking(req.getEnableDeepThinking());
        }

        if (updatedBy != null) entity.setUpdatedBy(updatedBy);
        Integer v = entity.getVersion();
        entity.setVersion(v == null ? 1 : Math.max(1, v + 1));

        PromptsEntity saved = promptsRepository.save(entity);
        return toDTO(saved);
    }

    private static PromptContentDTO toDTO(PromptsEntity p) {
        PromptContentDTO dto = new PromptContentDTO();
        dto.setPromptCode(p.getPromptCode());
        dto.setName(p.getName());
        dto.setSystemPrompt(p.getSystemPrompt());
        dto.setUserPromptTemplate(p.getUserPromptTemplate());
        dto.setTemperature(p.getTemperature());
        dto.setTopP(p.getTopP());
        dto.setMaxTokens(p.getMaxTokens());
        dto.setEnableDeepThinking(p.getEnableDeepThinking());
        dto.setVersion(p.getVersion());
        dto.setUpdatedBy(p.getUpdatedBy());
        return dto;
    }

    private static String normalizeCode(String code) {
        if (code == null) return "";
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String raw : codes) {
            String v = normalizeCode(raw);
            if (v.isBlank()) continue;
            if (!seen.add(v)) continue;
            out.add(v);
        }
        return out;
    }
}
