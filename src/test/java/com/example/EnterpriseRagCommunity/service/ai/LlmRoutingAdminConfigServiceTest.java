package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingPolicyDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingTargetDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyId;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LlmRoutingAdminConfigServiceTest {

    @Test
    void getAdminConfigSkipsInvalidPoliciesAndAppliesFallbacks() {
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);

        when(llmRoutingService.getPolicy(anyString())).thenReturn(new LlmRoutingService.Policy(
                LlmRoutingService.Strategy.WEIGHTED_RR,
                2,
                2,
                30_000
        ));

        LlmRoutingPolicyEntity nullId = new LlmRoutingPolicyEntity();

        LlmRoutingPolicyEntity nullTaskType = new LlmRoutingPolicyEntity();
        nullTaskType.setId(new LlmRoutingPolicyId("default", null));

        LlmRoutingPolicyEntity chat = new LlmRoutingPolicyEntity();
        chat.setId(new LlmRoutingPolicyId("default", "CHAT"));
        chat.setLabel("   ");
        chat.setCategory("");
        chat.setSortIndex(null);

        LlmRoutingPolicyEntity img = new LlmRoutingPolicyEntity();
        img.setId(new LlmRoutingPolicyId("default", "IMG"));
        img.setLabel("Image");
        img.setCategory("VISION");
        img.setSortIndex(5);

        List<LlmRoutingPolicyEntity> policyEntities = new ArrayList<>();
        policyEntities.add(null);
        policyEntities.add(nullId);
        policyEntities.add(nullTaskType);
        policyEntities.add(chat);
        policyEntities.add(img);
        when(llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(eq("default")))
                .thenReturn(policyEntities);

        LlmModelEntity m = new LlmModelEntity();
        m.setEnv("default");
        m.setPurpose("CHAT");
        m.setProviderId("p1");
        m.setModelName("m1");
        m.setEnabled(Boolean.TRUE);
        m.setWeight(7);
        m.setPriority(1);
        m.setSortIndex(2);
        m.setQps(9.9);
        m.setPriceConfigId(88L);
        m.setCreatedAt(LocalDateTime.now().minusDays(1));
        m.setUpdatedAt(LocalDateTime.now().minusDays(1));

        List<LlmModelEntity> models = new ArrayList<>();
        models.add(null);
        models.add(m);
        when(llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default")))
                .thenReturn(models);

        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                llmRoutingService,
                llmRoutingPolicyRepository,
                llmModelRepository
        );

        AdminLlmRoutingConfigDTO out = svc.getAdminConfig();

        assertNotNull(out.getScenarios());
        assertNotNull(out.getPolicies());
        assertNotNull(out.getTargets());

        assertEquals(2, out.getScenarios().size());
        assertEquals("CHAT", out.getScenarios().get(0).getTaskType());
        assertEquals("CHAT", out.getScenarios().get(0).getLabel());
        assertEquals("TEXT_GEN", out.getScenarios().get(0).getCategory());
        assertEquals(0, out.getScenarios().get(0).getSortIndex());

        assertEquals("IMG", out.getScenarios().get(1).getTaskType());
        assertEquals("Image", out.getScenarios().get(1).getLabel());
        assertEquals("VISION", out.getScenarios().get(1).getCategory());
        assertEquals(5, out.getScenarios().get(1).getSortIndex());

        assertEquals(2, out.getPolicies().size());
        assertEquals(1, out.getTargets().size());
        assertEquals("CHAT", out.getTargets().get(0).getTaskType());
        assertEquals("p1", out.getTargets().get(0).getProviderId());
        assertEquals("m1", out.getTargets().get(0).getModelName());
        assertEquals(Boolean.TRUE, out.getTargets().get(0).getEnabled());
        assertEquals(7, out.getTargets().get(0).getWeight());
        assertEquals(1, out.getTargets().get(0).getPriority());
        assertEquals(2, out.getTargets().get(0).getSortIndex());
        assertEquals(9.9, out.getTargets().get(0).getQps());
        assertEquals(88L, out.getTargets().get(0).getPriceConfigId());
    }

    @Test
    void updateAdminConfigThrowsOnNullPayload() {
        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                mock(LlmRoutingService.class),
                mock(LlmRoutingPolicyRepository.class),
                mock(LlmModelRepository.class)
        );
        assertThrows(IllegalArgumentException.class, () -> svc.updateAdminConfig(null, 1L));
    }

    @Test
    void updateAdminConfigHandlesNullPoliciesAndNullTargets() {
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);

        when(llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(eq("default"))).thenReturn(List.of());
        when(llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"))).thenReturn(List.of());
        when(llmRoutingService.getPolicy(anyString())).thenReturn(new LlmRoutingService.Policy(
                LlmRoutingService.Strategy.WEIGHTED_RR,
                2,
                2,
                30_000
        ));

        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                llmRoutingService,
                llmRoutingPolicyRepository,
                llmModelRepository
        );

        AdminLlmRoutingConfigDTO payload = new AdminLlmRoutingConfigDTO();
        AdminLlmRoutingConfigDTO out = svc.updateAdminConfig(payload, 7L);

        assertNotNull(out);
        verify(llmRoutingService, times(1)).resetRuntimeState();
        verify(llmModelRepository, never()).findByEnvAndProviderIdAndPurposeAndModelName(anyString(), anyString(), anyString(), anyString());
        verify(llmModelRepository, never()).save(any());
        verify(llmModelRepository, never()).delete(any());
        verify(llmRoutingPolicyRepository, never()).save(any());
    }

    @Test
    void updateAdminConfigCreatesNewPolicyEntityAndSaves() {
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);

        when(llmRoutingPolicyRepository.findById(any())).thenReturn(Optional.empty());
        when(llmRoutingPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(eq("default"))).thenReturn(List.of());
        when(llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"))).thenReturn(List.of());
        when(llmRoutingService.getPolicy(anyString())).thenReturn(new LlmRoutingService.Policy(
                LlmRoutingService.Strategy.WEIGHTED_RR,
                2,
                2,
                30_000
        ));

        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                llmRoutingService,
                llmRoutingPolicyRepository,
                llmModelRepository
        );

        AdminLlmRoutingPolicyDTO p = new AdminLlmRoutingPolicyDTO();
        p.setTaskType(" chat ");
        p.setStrategy(" weighted_rr ");
        p.setMaxAttempts(3);
        p.setFailureThreshold(4);
        p.setCooldownMs(500);

        AdminLlmRoutingConfigDTO payload = new AdminLlmRoutingConfigDTO();
        payload.setPolicies(List.of(p));

        svc.updateAdminConfig(payload, 9L);

        ArgumentCaptor<LlmRoutingPolicyEntity> captor = ArgumentCaptor.forClass(LlmRoutingPolicyEntity.class);
        verify(llmRoutingPolicyRepository, times(1)).save(captor.capture());
        LlmRoutingPolicyEntity saved = captor.getValue();

        assertNotNull(saved.getId());
        assertEquals("default", saved.getId().getEnv());
        assertEquals("CHAT", saved.getId().getTaskType());
        assertEquals("WEIGHTED_RR", saved.getStrategy());
        assertEquals(3, saved.getMaxAttempts());
        assertEquals(4, saved.getFailureThreshold());
        assertEquals(500, saved.getCooldownMs());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(9L, saved.getUpdatedBy());
    }

    @Test
    void updateAdminConfigSkipsNullPolicyAndInvalidTaskTypesAndKeepsExistingStrategyWhenNull() {
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);

        LlmRoutingPolicyEntity existing = new LlmRoutingPolicyEntity();
        existing.setId(new LlmRoutingPolicyId("default", "CHAT"));
        existing.setStrategy("WEIGHTED_RR");
        existing.setMaxAttempts(2);
        existing.setFailureThreshold(2);
        existing.setCooldownMs(30_000);

        when(llmRoutingPolicyRepository.findById(eq(new LlmRoutingPolicyId("default", "CHAT")))).thenReturn(Optional.of(existing));
        when(llmRoutingPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(eq("default"))).thenReturn(List.of(existing));
        when(llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"))).thenReturn(List.of());
        when(llmRoutingService.getPolicy(anyString())).thenReturn(new LlmRoutingService.Policy(
                LlmRoutingService.Strategy.WEIGHTED_RR,
                2,
                2,
                30_000
        ));

        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                llmRoutingService,
                llmRoutingPolicyRepository,
                llmModelRepository
        );

        AdminLlmRoutingPolicyDTO nullTaskType = new AdminLlmRoutingPolicyDTO();
        nullTaskType.setTaskType(null);

        AdminLlmRoutingPolicyDTO blankTaskType = new AdminLlmRoutingPolicyDTO();
        blankTaskType.setTaskType("   ");

        AdminLlmRoutingPolicyDTO valid = new AdminLlmRoutingPolicyDTO();
        valid.setTaskType("CHAT");
        valid.setStrategy(null);
        valid.setMaxAttempts(3);

        AdminLlmRoutingTargetDTO nullTargetTaskType = new AdminLlmRoutingTargetDTO();
        nullTargetTaskType.setTaskType(null);

        AdminLlmRoutingTargetDTO blankTargetTaskType = new AdminLlmRoutingTargetDTO();
        blankTargetTaskType.setTaskType("   ");

        AdminLlmRoutingConfigDTO payload = new AdminLlmRoutingConfigDTO();
        List<AdminLlmRoutingPolicyDTO> policies = new ArrayList<>();
        policies.add(null);
        policies.add(nullTaskType);
        policies.add(blankTaskType);
        policies.add(valid);
        payload.setPolicies(policies);

        List<AdminLlmRoutingTargetDTO> targets = new ArrayList<>();
        targets.add(null);
        targets.add(nullTargetTaskType);
        targets.add(blankTargetTaskType);
        payload.setTargets(targets);

        svc.updateAdminConfig(payload, 7L);

        ArgumentCaptor<LlmRoutingPolicyEntity> captor = ArgumentCaptor.forClass(LlmRoutingPolicyEntity.class);
        verify(llmRoutingPolicyRepository, times(1)).save(captor.capture());
        LlmRoutingPolicyEntity saved = captor.getValue();
        assertEquals("WEIGHTED_RR", saved.getStrategy());
        assertEquals(3, saved.getMaxAttempts());
    }

    @Test
    void updateAdminConfigSavesPolicyWhenOnlyEachFieldChanges() {
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);

        LlmRoutingPolicyEntity chat = new LlmRoutingPolicyEntity();
        chat.setId(new LlmRoutingPolicyId("default", "CHAT"));
        chat.setStrategy("WEIGHTED_RR");
        chat.setMaxAttempts(2);
        chat.setFailureThreshold(2);
        chat.setCooldownMs(30_000);

        LlmRoutingPolicyEntity img = new LlmRoutingPolicyEntity();
        img.setId(new LlmRoutingPolicyId("default", "IMG"));
        img.setStrategy("WEIGHTED_RR");
        img.setMaxAttempts(3);
        img.setFailureThreshold(2);
        img.setCooldownMs(40_000);

        LlmRoutingPolicyEntity embed = new LlmRoutingPolicyEntity();
        embed.setId(new LlmRoutingPolicyId("default", "EMBED"));
        embed.setStrategy("WEIGHTED_RR");
        embed.setMaxAttempts(4);
        embed.setFailureThreshold(4);
        embed.setCooldownMs(50_000);

        when(llmRoutingPolicyRepository.findById(eq(new LlmRoutingPolicyId("default", "CHAT")))).thenReturn(Optional.of(chat));
        when(llmRoutingPolicyRepository.findById(eq(new LlmRoutingPolicyId("default", "IMG")))).thenReturn(Optional.of(img));
        when(llmRoutingPolicyRepository.findById(eq(new LlmRoutingPolicyId("default", "EMBED")))).thenReturn(Optional.of(embed));
        when(llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(eq("default"))).thenReturn(List.of(chat, img, embed));
        when(llmRoutingPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"))).thenReturn(List.of());
        when(llmRoutingService.getPolicy(anyString())).thenReturn(new LlmRoutingService.Policy(
                LlmRoutingService.Strategy.WEIGHTED_RR,
                2,
                2,
                30_000
        ));

        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                llmRoutingService,
                llmRoutingPolicyRepository,
                llmModelRepository
        );

        AdminLlmRoutingPolicyDTO pChat = new AdminLlmRoutingPolicyDTO();
        pChat.setTaskType("CHAT");
        pChat.setStrategy(" least_latency ");

        AdminLlmRoutingPolicyDTO pImg = new AdminLlmRoutingPolicyDTO();
        pImg.setTaskType("IMG");
        pImg.setMaxAttempts(3);
        pImg.setFailureThreshold(9);
        pImg.setCooldownMs(40_000);

        AdminLlmRoutingPolicyDTO pEmbed = new AdminLlmRoutingPolicyDTO();
        pEmbed.setTaskType("EMBED");
        pEmbed.setMaxAttempts(4);
        pEmbed.setFailureThreshold(4);
        pEmbed.setCooldownMs(12_345);

        AdminLlmRoutingConfigDTO payload = new AdminLlmRoutingConfigDTO();
        payload.setPolicies(List.of(pChat, pImg, pEmbed));
        payload.setTargets(List.of());

        svc.updateAdminConfig(payload, 12L);

        verify(llmRoutingPolicyRepository, times(3)).save(any(LlmRoutingPolicyEntity.class));
    }

    @Test
    void updateAdminConfigDoesNotSavePolicyWhenNoChange() {
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);

        LlmRoutingPolicyEntity existing = new LlmRoutingPolicyEntity();
        existing.setId(new LlmRoutingPolicyId("default", "CHAT"));
        existing.setStrategy("WEIGHTED_RR");
        existing.setMaxAttempts(2);
        existing.setFailureThreshold(2);
        existing.setCooldownMs(30_000);

        when(llmRoutingPolicyRepository.findById(eq(new LlmRoutingPolicyId("default", "CHAT")))).thenReturn(Optional.of(existing));
        when(llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(eq("default"))).thenReturn(List.of(existing));
        when(llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"))).thenReturn(List.of());
        when(llmRoutingService.getPolicy(anyString())).thenReturn(new LlmRoutingService.Policy(
                LlmRoutingService.Strategy.WEIGHTED_RR,
                2,
                2,
                30_000
        ));

        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                llmRoutingService,
                llmRoutingPolicyRepository,
                llmModelRepository
        );

        AdminLlmRoutingPolicyDTO p = new AdminLlmRoutingPolicyDTO();
        p.setTaskType("CHAT");
        p.setStrategy("  ");

        AdminLlmRoutingConfigDTO payload = new AdminLlmRoutingConfigDTO();
        payload.setPolicies(List.of(p));
        payload.setTargets(List.of());

        svc.updateAdminConfig(payload, 7L);

        verify(llmRoutingPolicyRepository, never()).save(any());
    }

    @Test
    void updateAdminConfigReconcilesTargetsUpsertsAndDeletes() {
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);

        when(llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(eq("default"))).thenReturn(List.of());
        when(llmRoutingService.getPolicy(anyString())).thenReturn(new LlmRoutingService.Policy(
                LlmRoutingService.Strategy.WEIGHTED_RR,
                2,
                2,
                30_000
        ));

        LlmModelEntity existing1 = new LlmModelEntity();
        existing1.setEnv("default");
        existing1.setProviderId("p1");
        existing1.setPurpose("CHAT");
        existing1.setModelName("m1");
        existing1.setEnabled(Boolean.TRUE);
        existing1.setIsDefault(Boolean.FALSE);
        existing1.setWeight(1);
        existing1.setPriority(1);
        existing1.setSortIndex(1);
        existing1.setCreatedAt(LocalDateTime.now().minusDays(1));
        existing1.setUpdatedAt(LocalDateTime.now().minusDays(1));

        LlmModelEntity existingNoKeep = new LlmModelEntity();
        existingNoKeep.setEnv("default");
        existingNoKeep.setProviderId("p3");
        existingNoKeep.setPurpose("CHAT");
        existingNoKeep.setModelName("m3");
        existingNoKeep.setEnabled(Boolean.TRUE);
        existingNoKeep.setIsDefault(Boolean.FALSE);
        existingNoKeep.setWeight(1);
        existingNoKeep.setPriority(1);
        existingNoKeep.setSortIndex(1);
        existingNoKeep.setCreatedAt(LocalDateTime.now().minusDays(1));
        existingNoKeep.setUpdatedAt(LocalDateTime.now().minusDays(1));

        LlmModelEntity existingNullProvider = new LlmModelEntity();
        existingNullProvider.setEnv("default");
        existingNullProvider.setProviderId(null);
        existingNullProvider.setPurpose("CHAT");
        existingNullProvider.setModelName("m4");

        LlmModelEntity existingNullModelName = new LlmModelEntity();
        existingNullModelName.setEnv("default");
        existingNullModelName.setProviderId("p4");
        existingNullModelName.setPurpose("CHAT");
        existingNullModelName.setModelName("   ");

        LlmModelEntity blankPurpose = new LlmModelEntity();
        blankPurpose.setEnv("default");
        blankPurpose.setProviderId("x");
        blankPurpose.setPurpose("   ");
        blankPurpose.setModelName("y");

        LlmModelEntity otherPurpose = new LlmModelEntity();
        otherPurpose.setEnv("default");
        otherPurpose.setProviderId("o");
        otherPurpose.setPurpose("OTHER");
        otherPurpose.setModelName("m");

        List<LlmModelEntity> allModels = new ArrayList<>();
        allModels.add(null);
        allModels.add(blankPurpose);
        allModels.add(otherPurpose);
        allModels.add(existing1);
        allModels.add(existingNoKeep);
        allModels.add(existingNullProvider);
        allModels.add(existingNullModelName);
        when(llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default")))
                .thenReturn(allModels);

        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("CHAT"), eq("m1")))
                .thenReturn(Optional.of(existing1));
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("pNew"), eq("CHAT"), eq("mNew")))
                .thenReturn(Optional.empty());
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("pZero"), eq("CHAT"), eq("mZero")))
                .thenReturn(Optional.empty());
        when(llmModelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                llmRoutingService,
                llmRoutingPolicyRepository,
                llmModelRepository
        );

        AdminLlmRoutingTargetDTO skipNullProvider = new AdminLlmRoutingTargetDTO();
        skipNullProvider.setTaskType("CHAT");
        skipNullProvider.setProviderId(null);
        skipNullProvider.setModelName("mX");

        AdminLlmRoutingTargetDTO skipNullModelName = new AdminLlmRoutingTargetDTO();
        skipNullModelName.setTaskType("CHAT");
        skipNullModelName.setProviderId("pSkip");
        skipNullModelName.setModelName("   ");

        AdminLlmRoutingTargetDTO t1 = new AdminLlmRoutingTargetDTO();
        t1.setTaskType("chat");
        t1.setProviderId(" p1 ");
        t1.setModelName(" m1 ");
        t1.setEnabled(Boolean.FALSE);
        t1.setWeight(5);
        t1.setPriority(6);
        t1.setSortIndex(7);
        t1.setQps(0.0001);

        AdminLlmRoutingTargetDTO tNew = new AdminLlmRoutingTargetDTO();
        tNew.setTaskType("CHAT");
        tNew.setProviderId("pNew");
        tNew.setModelName("mNew");
        tNew.setEnabled(null);
        tNew.setWeight(null);
        tNew.setPriority(null);
        tNew.setSortIndex(null);
        tNew.setQps(200000.0);

        AdminLlmRoutingTargetDTO tZero = new AdminLlmRoutingTargetDTO();
        tZero.setTaskType("CHAT");
        tZero.setProviderId("pZero");
        tZero.setModelName("mZero");
        tZero.setEnabled(Boolean.TRUE);
        tZero.setQps(0.0);

        AdminLlmRoutingConfigDTO payload = new AdminLlmRoutingConfigDTO();
        List<AdminLlmRoutingTargetDTO> targets = new ArrayList<>();
        targets.add(null);
        targets.add(skipNullProvider);
        targets.add(skipNullModelName);
        targets.add(t1);
        targets.add(tNew);
        targets.add(tZero);
        payload.setTargets(targets);

        svc.updateAdminConfig(payload, 7L);

        ArgumentCaptor<LlmModelEntity> modelCaptor = ArgumentCaptor.forClass(LlmModelEntity.class);
        verify(llmModelRepository, times(3)).save(modelCaptor.capture());

        List<LlmModelEntity> savedModels = modelCaptor.getAllValues();
        assertEquals(3, savedModels.size());

        LlmModelEntity savedExisting = savedModels.stream().filter(x -> "p1".equals(x.getProviderId()) && "m1".equals(x.getModelName())).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, savedExisting.getEnabled());
        assertEquals(5, savedExisting.getWeight());
        assertEquals(6, savedExisting.getPriority());
        assertEquals(7, savedExisting.getSortIndex());
        assertEquals(0.001, savedExisting.getQps(), 0.0);
        assertEquals(7L, savedExisting.getUpdatedBy());
        assertNotNull(savedExisting.getUpdatedAt());

        LlmModelEntity savedNew = savedModels.stream().filter(x -> "pNew".equals(x.getProviderId()) && "mNew".equals(x.getModelName())).findFirst().orElseThrow();
        assertEquals("default", savedNew.getEnv());
        assertEquals("CHAT", savedNew.getPurpose());
        assertEquals(Boolean.TRUE, savedNew.getEnabled());
        assertEquals(0, savedNew.getWeight());
        assertEquals(0, savedNew.getPriority());
        assertEquals(0, savedNew.getSortIndex());
        assertEquals(100_000.0, savedNew.getQps(), 0.0);
        assertEquals(7L, savedNew.getCreatedBy());
        assertNotNull(savedNew.getCreatedAt());
        assertEquals(7L, savedNew.getUpdatedBy());
        assertNotNull(savedNew.getUpdatedAt());

        LlmModelEntity savedZero = savedModels.stream().filter(x -> "pZero".equals(x.getProviderId()) && "mZero".equals(x.getModelName())).findFirst().orElseThrow();
        assertNull(savedZero.getQps());

        verify(llmModelRepository, times(1)).delete(eq(existingNoKeep));
        verify(llmModelRepository, never()).delete(eq(existing1));
        verify(llmRoutingService, times(1)).resetRuntimeState();
    }

    @Test
    void normalizePrivateMethodsViaReflection() throws Exception {
        Method normalizeNonBlank = LlmRoutingAdminConfigService.class.getDeclaredMethod("normalizeNonBlank", String.class);
        normalizeNonBlank.setAccessible(true);
        assertNull(normalizeNonBlank.invoke(null, (String) null));
        assertNull(normalizeNonBlank.invoke(null, "   "));
        assertEquals("a", normalizeNonBlank.invoke(null, " a "));

        Method normalizeTaskType = LlmRoutingAdminConfigService.class.getDeclaredMethod("normalizeTaskType", String.class);
        normalizeTaskType.setAccessible(true);
        assertNull(normalizeTaskType.invoke(null, (String) null));
        assertEquals("CHAT", normalizeTaskType.invoke(null, " chat "));

        Method normalizePositiveIntOrNull = LlmRoutingAdminConfigService.class.getDeclaredMethod("normalizePositiveIntOrNull", Integer.class, int.class, int.class);
        normalizePositiveIntOrNull.setAccessible(true);
        assertNull(normalizePositiveIntOrNull.invoke(null, (Integer) null, 2, 5));
        assertNull(normalizePositiveIntOrNull.invoke(null, 0, 2, 5));
        assertNull(normalizePositiveIntOrNull.invoke(null, -1, 2, 5));
        assertEquals(2, normalizePositiveIntOrNull.invoke(null, 1, 2, 5));
        assertEquals(5, normalizePositiveIntOrNull.invoke(null, 6, 2, 5));
        assertEquals(3, normalizePositiveIntOrNull.invoke(null, 3, 2, 5));

        Method normalizePositiveDoubleOrNull = LlmRoutingAdminConfigService.class.getDeclaredMethod("normalizePositiveDoubleOrNull", Double.class, double.class, double.class);
        normalizePositiveDoubleOrNull.setAccessible(true);
        assertNull(normalizePositiveDoubleOrNull.invoke(null, (Double) null, 0.001, 100_000.0));
        assertNull(normalizePositiveDoubleOrNull.invoke(null, 0.0, 0.001, 100_000.0));
        assertNull(normalizePositiveDoubleOrNull.invoke(null, -0.1, 0.001, 100_000.0));
        assertEquals(0.001, (Double) normalizePositiveDoubleOrNull.invoke(null, 0.0001, 0.001, 100_000.0), 0.0);
        assertEquals(100_000.0, (Double) normalizePositiveDoubleOrNull.invoke(null, 200000.0, 0.001, 100_000.0), 0.0);
        assertEquals(1.5, (Double) normalizePositiveDoubleOrNull.invoke(null, 1.5, 0.001, 100_000.0), 0.0);
    }
}
