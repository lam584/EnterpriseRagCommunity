package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingPolicyDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingScenarioDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingTargetDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyId;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LlmRoutingAdminConfigService {

    private static final String ENV_DEFAULT = "default";

    private final LlmRoutingService llmRoutingService;
    private final LlmRoutingPolicyRepository llmRoutingPolicyRepository;
    private final LlmModelRepository llmModelRepository;

    @Transactional(readOnly = true)
    public AdminLlmRoutingConfigDTO getAdminConfig() {
        AdminLlmRoutingConfigDTO out = new AdminLlmRoutingConfigDTO();

        List<LlmRoutingPolicyEntity> policyEntities = llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(ENV_DEFAULT);
        List<AdminLlmRoutingScenarioDTO> scenarios = new ArrayList<>();
        List<AdminLlmRoutingPolicyDTO> policies = new ArrayList<>();

        for (LlmRoutingPolicyEntity pe : policyEntities) {
            if (pe == null || pe.getId() == null || pe.getId().getTaskType() == null) continue;
            String taskType = pe.getId().getTaskType();

            AdminLlmRoutingScenarioDTO sd = new AdminLlmRoutingScenarioDTO();
            sd.setTaskType(taskType);
            sd.setLabel(pe.getLabel() == null || pe.getLabel().isBlank() ? taskType : pe.getLabel());
            sd.setCategory(pe.getCategory() == null || pe.getCategory().isBlank() ? "TEXT_GEN" : pe.getCategory());
            sd.setSortIndex(pe.getSortIndex() == null ? 0 : pe.getSortIndex());
            scenarios.add(sd);

            AdminLlmRoutingPolicyDTO p = new AdminLlmRoutingPolicyDTO();
            p.setTaskType(taskType);
            LlmRoutingService.Policy pol = llmRoutingService.getPolicy(taskType);
            p.setStrategy(pol.strategy().name());
            p.setMaxAttempts(pol.maxAttempts());
            p.setFailureThreshold(pol.failureThreshold());
            p.setCooldownMs(pol.cooldownMs());
            policies.add(p);
        }
        out.setScenarios(scenarios);
        out.setPolicies(policies);

        List<LlmModelEntity> models = llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(ENV_DEFAULT);
        List<AdminLlmRoutingTargetDTO> targets = new ArrayList<>();
        for (LlmModelEntity m : models) {
            if (m == null) continue;
            AdminLlmRoutingTargetDTO t = new AdminLlmRoutingTargetDTO();
            t.setTaskType(m.getPurpose());
            t.setProviderId(m.getProviderId());
            t.setModelName(m.getModelName());
            t.setEnabled(m.getEnabled());
            t.setWeight(m.getWeight());
            t.setPriority(m.getPriority());
            t.setSortIndex(m.getSortIndex());
            t.setQps(m.getQps());
            t.setPriceConfigId(m.getPriceConfigId());
            targets.add(t);
        }
        out.setTargets(targets);

        return out;
    }

    @Transactional
    public AdminLlmRoutingConfigDTO updateAdminConfig(AdminLlmRoutingConfigDTO payload, Long actorUserId) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");
        LocalDateTime now = LocalDateTime.now();

        List<AdminLlmRoutingPolicyDTO> policies = payload.getPolicies() == null ? List.of() : payload.getPolicies();
        Set<String> taskTypesToReconcile = new HashSet<>();
        for (AdminLlmRoutingPolicyDTO p : policies) {
            if (p == null) continue;
            String taskType = normalizeTaskType(p.getTaskType());
            if (taskType == null) continue;
            taskTypesToReconcile.add(taskType);

            LlmRoutingPolicyId id = new LlmRoutingPolicyId(ENV_DEFAULT, taskType);
            LlmRoutingPolicyEntity e = llmRoutingPolicyRepository.findById(id).orElse(null);
            boolean isNew = (e == null);
            if (isNew) {
                e = new LlmRoutingPolicyEntity();
                e.setId(id);
                e.setStrategy("WEIGHTED_RR");
                e.setMaxAttempts(2);
                e.setFailureThreshold(2);
                e.setCooldownMs(30_000);
                e.setProbeEnabled(Boolean.FALSE);
                e.setProbeIntervalMs(null);
                e.setProbePath(null);
                e.setSortIndex(0);
                e.setLabel(null);
                e.setCategory(null);
            }

            String nextStrategy = e.getStrategy();
            if (p.getStrategy() != null && !p.getStrategy().isBlank()) {
                nextStrategy = p.getStrategy().trim().toUpperCase(Locale.ROOT);
            }
            Integer nextMaxAttempts = (p.getMaxAttempts() != null) ? p.getMaxAttempts() : e.getMaxAttempts();
            Integer nextFailureThreshold = (p.getFailureThreshold() != null) ? p.getFailureThreshold() : e.getFailureThreshold();
            Integer nextCooldownMs = (p.getCooldownMs() != null) ? p.getCooldownMs() : e.getCooldownMs();

            boolean changed = isNew
                    || !Objects.equals(nextStrategy, e.getStrategy())
                    || !Objects.equals(nextMaxAttempts, e.getMaxAttempts())
                    || !Objects.equals(nextFailureThreshold, e.getFailureThreshold())
                    || !Objects.equals(nextCooldownMs, e.getCooldownMs());
            if (!changed) continue;

            e.setStrategy(nextStrategy);
            e.setMaxAttempts(nextMaxAttempts);
            e.setFailureThreshold(nextFailureThreshold);
            e.setCooldownMs(nextCooldownMs);
            e.setUpdatedAt(now);
            e.setUpdatedBy(actorUserId);
            llmRoutingPolicyRepository.save(e);
        }

        List<AdminLlmRoutingTargetDTO> targets = payload.getTargets() == null ? List.of() : payload.getTargets();
        Map<String, List<AdminLlmRoutingTargetDTO>> byTask = new HashMap<>();
        for (AdminLlmRoutingTargetDTO t : targets) {
            if (t == null) continue;
            String taskType = normalizeTaskType(t.getTaskType());
            if (taskType == null) continue;
            byTask.computeIfAbsent(taskType, k -> new ArrayList<>()).add(t);
            taskTypesToReconcile.add(taskType);
        }

        Map<String, List<LlmModelEntity>> existingByPurpose = new HashMap<>();
        if (!taskTypesToReconcile.isEmpty()) {
            List<LlmModelEntity> all = llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(ENV_DEFAULT);
            for (LlmModelEntity e : all) {
                if (e == null) continue;
                String purpose = normalizeTaskType(e.getPurpose());
                if (purpose == null) continue;
                if (!taskTypesToReconcile.contains(purpose)) continue;
                existingByPurpose.computeIfAbsent(purpose, k -> new ArrayList<>()).add(e);
            }
        }

        for (String purpose : taskTypesToReconcile) {
            List<AdminLlmRoutingTargetDTO> incoming = byTask.getOrDefault(purpose, List.of());
            Set<String> keepKeys = new HashSet<>();

            for (AdminLlmRoutingTargetDTO t : incoming) {
                if (t == null) continue;
                String providerId = normalizeNonBlank(t.getProviderId());
                String modelName = normalizeNonBlank(t.getModelName());
                if (providerId == null || modelName == null) continue;

                String key = providerId + "|" + modelName;
                keepKeys.add(key);

                LlmModelEntity e = llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(ENV_DEFAULT, providerId, purpose, modelName).orElse(null);
                boolean isNew = (e == null);
                if (isNew) {
                    e = new LlmModelEntity();
                    e.setEnv(ENV_DEFAULT);
                    e.setProviderId(providerId);
                    e.setPurpose(purpose);
                    e.setModelName(modelName);
                    e.setCreatedAt(now);
                    e.setCreatedBy(actorUserId);
                    e.setIsDefault(Boolean.FALSE);
                }

                e.setEnabled(t.getEnabled() == null || Boolean.TRUE.equals(t.getEnabled()));
                e.setWeight(t.getWeight() == null ? 0 : t.getWeight());
                e.setPriority(t.getPriority() == null ? 0 : t.getPriority());
                e.setSortIndex(t.getSortIndex() == null ? 0 : t.getSortIndex());
                e.setQps(normalizePositiveDoubleOrNull(t.getQps(), 0.001, 100_000.0));
                e.setPriceConfigId(t.getPriceConfigId());
                e.setUpdatedAt(now);
                e.setUpdatedBy(actorUserId);
                llmModelRepository.save(e);
            }

            for (LlmModelEntity e : existingByPurpose.getOrDefault(purpose, List.of())) {
                String providerId = normalizeNonBlank(e.getProviderId());
                String modelName = normalizeNonBlank(e.getModelName());
                if (providerId == null || modelName == null) continue;
                String key = providerId + "|" + modelName;
                if (keepKeys.contains(key)) continue;
                llmModelRepository.delete(e);
            }
        }

        llmRoutingService.resetRuntimeState();

        return getAdminConfig();
    }

    private static String normalizeTaskType(String s) {
        String t = normalizeNonBlank(s);
        if (t == null) return null;
        return t.toUpperCase(Locale.ROOT);
    }

    private static String normalizeNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static Integer normalizePositiveIntOrNull(Integer v, int min, int max) {
        if (v == null) return null;
        int x = v;
        if (x <= 0) return null;
        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    private static Double normalizePositiveDoubleOrNull(Double v, double min, double max) {
        if (v == null) return null;
        double x = v;
        if (!(x > 0.0)) return null;
        if (x < min) return min;
        if (x > max) return max;
        return x;
    }
}
