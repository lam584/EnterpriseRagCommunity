package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigPricingDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigPricingTierDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigUpsertRequest;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmPriceConfigAdminServiceTest {

    @Test
    void listAll_whenEmpty_returnsEmptyList() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of());

        List<AdminLlmPriceConfigDTO> result = service.listAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void listAll_skipsNullEntity_andParsesPricingMetadata() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity entity = new LlmPriceConfigEntity();
        entity.setId(1L);
        entity.setName("MODEL_A");
        entity.setCurrency("CNY");
        entity.setInputCostPer1k(BigDecimal.ONE);
        entity.setOutputCostPer1k(BigDecimal.TEN);
        entity.setUpdatedAt(LocalDateTime.now());

        Map<String, Object> pricing = new HashMap<>();
        pricing.put("strategy", " FLAT ");
        pricing.put("unit", " ");
        pricing.put("defaultInputCostPerUnit", new BigDecimal("1.23"));
        pricing.put("defaultOutputCostPerUnit", 2);
        pricing.put("nonThinkingInputCostPerUnit", 4.5d);
        pricing.put("nonThinkingOutputCostPerUnit", "7.89");
        pricing.put("thinkingInputCostPerUnit", "x");
        pricing.put("thinkingOutputCostPerUnit", new Object());

        List<Object> tiers = new ArrayList<>();
        tiers.add("invalid");

        Map<String, Object> badTier = new HashMap<>();
        badTier.put("upToTokens", "bad-long");
        tiers.add(badTier);

        Map<String, Object> goodTier = new HashMap<>();
        goodTier.put("upToTokens", 200L);
        goodTier.put("inputCostPerUnit", "3.5");
        goodTier.put("outputCostPerUnit", 7);
        tiers.add(goodTier);

        pricing.put("tiers", tiers);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pricing", pricing);
        entity.setMetadata(metadata);

        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(Arrays.asList(null, entity));

        List<AdminLlmPriceConfigDTO> result = service.listAll();

        assertEquals(1, result.size());
        AdminLlmPriceConfigDTO dto = result.get(0);
        assertEquals("MODEL_A", dto.getName());
        assertNotNull(dto.getPricing());
        assertEquals("FLAT", dto.getPricing().getStrategy());
        assertNull(dto.getPricing().getUnit());
        assertEquals(new BigDecimal("1.23"), dto.getPricing().getDefaultInputCostPerUnit());
        assertEquals(BigDecimal.valueOf(2), dto.getPricing().getDefaultOutputCostPerUnit());
        assertEquals(BigDecimal.valueOf(4.5d), dto.getPricing().getNonThinkingInputCostPerUnit());
        assertEquals(new BigDecimal("7.89"), dto.getPricing().getNonThinkingOutputCostPerUnit());
        assertNull(dto.getPricing().getThinkingInputCostPerUnit());
        assertNull(dto.getPricing().getThinkingOutputCostPerUnit());
        assertNotNull(dto.getPricing().getTiers());
        assertEquals(1, dto.getPricing().getTiers().size());
        assertEquals(200L, dto.getPricing().getTiers().get(0).getUpToTokens());
    }

    @Test
    void listAll_whenPricingMissingOrWrongType_setsPricingNull() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity noMetadata = new LlmPriceConfigEntity();
        noMetadata.setName("A");

        LlmPriceConfigEntity wrongPricingType = new LlmPriceConfigEntity();
        wrongPricingType.setName("B");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pricing", "not-map");
        wrongPricingType.setMetadata(metadata);

        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(noMetadata, wrongPricingType));

        List<AdminLlmPriceConfigDTO> result = service.listAll();

        assertEquals(2, result.size());
        assertNull(result.get(0).getPricing());
        assertNull(result.get(1).getPricing());
    }

    @Test
    void upsert_whenReqNull_throwsIllegalArgumentException() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.upsert(null, 1L));
    }

    @Test
    void upsert_whenNameBlank_throwsIllegalArgumentException() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("   ");

        assertThrows(IllegalArgumentException.class, () -> service.upsert(req, 1L));
    }

    @Test
    void upsert_createNew_withoutPricing_setsDefaultsAndAuditFields() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName(" model_1 ");
        req.setCurrency(" usd ");
        req.setInputCostPer1k(new BigDecimal("0.12"));
        req.setOutputCostPer1k(new BigDecimal("0.34"));

        when(repository.findByName("model_1")).thenReturn(Optional.empty());
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminLlmPriceConfigDTO result = service.upsert(req, 77L);

        ArgumentCaptor<LlmPriceConfigEntity> captor = ArgumentCaptor.forClass(LlmPriceConfigEntity.class);
        verify(repository).save(captor.capture());
        LlmPriceConfigEntity saved = captor.getValue();

        assertEquals("model_1", saved.getName());
        assertEquals("USD", saved.getCurrency());
        assertEquals(new BigDecimal("0.12"), saved.getInputCostPer1k());
        assertEquals(new BigDecimal("0.34"), saved.getOutputCostPer1k());
        assertEquals(77L, saved.getCreatedBy());
        assertNotNull(saved.getCreatedAt());
        assertEquals(77L, saved.getUpdatedBy());
        assertNotNull(saved.getUpdatedAt());
        assertEquals("model_1", result.getName());
    }

    @Test
    void upsert_updateExisting_blankCurrencyDoesNotOverride_andPricingNullKeepsCosts() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity existing = new LlmPriceConfigEntity();
        existing.setName("MODEL_X");
        existing.setCurrency("CNY");
        existing.setInputCostPer1k(new BigDecimal("9"));
        existing.setOutputCostPer1k(new BigDecimal("8"));
        existing.setCreatedAt(LocalDateTime.now().minusDays(1));
        existing.setCreatedBy(11L);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("MODEL_X");
        req.setCurrency("   ");

        when(repository.findByName("MODEL_X")).thenReturn(Optional.of(existing));
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 99L);

        assertEquals("CNY", existing.getCurrency());
        assertEquals(new BigDecimal("9"), existing.getInputCostPer1k());
        assertEquals(new BigDecimal("8"), existing.getOutputCostPer1k());
        assertEquals(11L, existing.getCreatedBy());
        assertNotNull(existing.getUpdatedAt());
        assertEquals(99L, existing.getUpdatedBy());
    }

    @Test
    void upsert_flatPer1m_dividesByThousand_withHalfUpScale8() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M1");

        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy("FLAT");
        pricing.setUnit("PER_1M");
        pricing.setDefaultInputCostPerUnit(new BigDecimal("100.123456789"));
        pricing.setDefaultOutputCostPerUnit(new BigDecimal("200"));
        req.setPricing(pricing);

        when(repository.findByName("M1")).thenReturn(Optional.empty());
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 1L);

        ArgumentCaptor<LlmPriceConfigEntity> captor = ArgumentCaptor.forClass(LlmPriceConfigEntity.class);
        verify(repository).save(captor.capture());
        LlmPriceConfigEntity saved = captor.getValue();

        assertEquals(new BigDecimal("0.10012346"), saved.getInputCostPer1k());
        assertEquals(new BigDecimal("0.20000000"), saved.getOutputCostPer1k());
        assertNotNull(saved.getMetadata());
        assertTrue(saved.getMetadata().containsKey("pricing"));
    }

    @Test
    void upsert_flatPer1kOrNullUnit_setsValuesDirectly_andAllowsNullDefaults() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M2");

        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy(null);
        pricing.setUnit(null);
        pricing.setDefaultInputCostPerUnit(null);
        pricing.setDefaultOutputCostPerUnit(new BigDecimal("5"));
        req.setPricing(pricing);

        when(repository.findByName("M2")).thenReturn(Optional.empty());
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 2L);

        ArgumentCaptor<LlmPriceConfigEntity> captor = ArgumentCaptor.forClass(LlmPriceConfigEntity.class);
        verify(repository).save(captor.capture());
        LlmPriceConfigEntity saved = captor.getValue();

        assertNull(saved.getInputCostPer1k());
        assertEquals(new BigDecimal("5"), saved.getOutputCostPer1k());
    }

    @Test
    void upsert_flatPer1k_explicitUnit_setsValuesDirectly() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M2B");

        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy("FLAT");
        pricing.setUnit("PER_1K");
        pricing.setDefaultInputCostPerUnit(new BigDecimal("1.11"));
        pricing.setDefaultOutputCostPerUnit(new BigDecimal("2.22"));
        req.setPricing(pricing);

        when(repository.findByName("M2B")).thenReturn(Optional.empty());
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 12L);

        ArgumentCaptor<LlmPriceConfigEntity> captor = ArgumentCaptor.forClass(LlmPriceConfigEntity.class);
        verify(repository).save(captor.capture());
        LlmPriceConfigEntity saved = captor.getValue();

        assertEquals(new BigDecimal("1.11"), saved.getInputCostPer1k());
        assertEquals(new BigDecimal("2.22"), saved.getOutputCostPer1k());
    }

    @Test
    void upsert_tieredStrategy_resetsInputOutputToNull() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity existing = new LlmPriceConfigEntity();
        existing.setName("M3");
        existing.setCurrency("CNY");
        existing.setInputCostPer1k(new BigDecimal("1"));
        existing.setOutputCostPer1k(new BigDecimal("2"));

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M3");
        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy("TIERED");
        pricing.setUnit("PER_1K");
        req.setPricing(pricing);

        when(repository.findByName("M3")).thenReturn(Optional.of(existing));
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 3L);

        assertNull(existing.getInputCostPer1k());
        assertNull(existing.getOutputCostPer1k());
    }

    @Test
    @SuppressWarnings("unchecked")
    void upsert_tiers_filtersInvalidAndKeepsValidOnly() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M4");

        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy("TIERED");
        pricing.setUnit("PER_1K");

        AdminLlmPriceConfigPricingTierDTO nullTier = null;

        AdminLlmPriceConfigPricingTierDTO invalid1 = new AdminLlmPriceConfigPricingTierDTO();
        invalid1.setUpToTokens(0L);

        AdminLlmPriceConfigPricingTierDTO invalid2 = new AdminLlmPriceConfigPricingTierDTO();
        invalid2.setUpToTokens(null);

        AdminLlmPriceConfigPricingTierDTO valid = new AdminLlmPriceConfigPricingTierDTO();
        valid.setUpToTokens(1000L);
        valid.setInputCostPerUnit(new BigDecimal("1.2"));
        valid.setOutputCostPerUnit(new BigDecimal("2.3"));

        pricing.setTiers(Arrays.asList(nullTier, invalid1, invalid2, valid));
        req.setPricing(pricing);

        when(repository.findByName("M4")).thenReturn(Optional.empty());
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 4L);

        ArgumentCaptor<LlmPriceConfigEntity> captor = ArgumentCaptor.forClass(LlmPriceConfigEntity.class);
        verify(repository).save(captor.capture());

        LlmPriceConfigEntity saved = captor.getValue();
        Map<String, Object> metadata = saved.getMetadata();
        assertNotNull(metadata);
        Map<String, Object> pricingMap = (Map<String, Object>) metadata.get("pricing");
        assertNotNull(pricingMap);
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) pricingMap.get("tiers");
        assertNotNull(tiers);
        assertEquals(1, tiers.size());
        assertEquals(1000L, tiers.get(0).get("upToTokens"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void upsert_whenAllTiersInvalid_doesNotWriteTiersKey() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M5");

        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy("FLAT");
        pricing.setUnit("PER_1K");

        AdminLlmPriceConfigPricingTierDTO invalid = new AdminLlmPriceConfigPricingTierDTO();
        invalid.setUpToTokens(-1L);
        pricing.setTiers(List.of(invalid));
        req.setPricing(pricing);

        when(repository.findByName("M5")).thenReturn(Optional.empty());
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 5L);

        ArgumentCaptor<LlmPriceConfigEntity> captor = ArgumentCaptor.forClass(LlmPriceConfigEntity.class);
        verify(repository).save(captor.capture());

        Map<String, Object> pricingMap = (Map<String, Object>) captor.getValue().getMetadata().get("pricing");
        assertFalse(pricingMap.containsKey("tiers"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void upsert_pricingOnExistingEntity_preservesOtherMetadataKeys() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity existing = new LlmPriceConfigEntity();
        existing.setName("M6");
        Map<String, Object> oldMeta = new HashMap<>();
        oldMeta.put("keep", "yes");
        existing.setMetadata(oldMeta);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M6");
        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy("UNKNOWN");
        pricing.setUnit("WHATEVER");
        req.setPricing(pricing);

        when(repository.findByName("M6")).thenReturn(Optional.of(existing));
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 6L);

        ArgumentCaptor<LlmPriceConfigEntity> captor = ArgumentCaptor.forClass(LlmPriceConfigEntity.class);
        verify(repository).save(captor.capture());
        Map<String, Object> savedMeta = captor.getValue().getMetadata();
        assertEquals("yes", savedMeta.get("keep"));
        assertTrue(savedMeta.containsKey("pricing"));
    }

    @Test
    void upsert_unknownStrategy_keepsExistingCostsUnchanged() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity existing = new LlmPriceConfigEntity();
        existing.setName("M7");
        existing.setInputCostPer1k(new BigDecimal("6.6"));
        existing.setOutputCostPer1k(new BigDecimal("7.7"));

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M7");
        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy("CUSTOM");
        pricing.setUnit("PER_1M");
        pricing.setDefaultInputCostPerUnit(new BigDecimal("123"));
        pricing.setDefaultOutputCostPerUnit(new BigDecimal("456"));
        req.setPricing(pricing);

        when(repository.findByName("M7")).thenReturn(Optional.of(existing));
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 7L);

        assertEquals(new BigDecimal("6.6"), existing.getInputCostPer1k());
        assertEquals(new BigDecimal("7.7"), existing.getOutputCostPer1k());
    }

    @Test
    void upsert_flatWithUnknownUnit_keepsExistingCostsUnchanged() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity existing = new LlmPriceConfigEntity();
        existing.setName("M7B");
        existing.setInputCostPer1k(new BigDecimal("8.8"));
        existing.setOutputCostPer1k(new BigDecimal("9.9"));

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        req.setName("M7B");
        AdminLlmPriceConfigPricingDTO pricing = new AdminLlmPriceConfigPricingDTO();
        pricing.setStrategy("FLAT");
        pricing.setUnit("PER_UNKNOWN");
        pricing.setDefaultInputCostPerUnit(new BigDecimal("111"));
        pricing.setDefaultOutputCostPerUnit(new BigDecimal("222"));
        req.setPricing(pricing);

        when(repository.findByName("M7B")).thenReturn(Optional.of(existing));
        when(repository.save(any(LlmPriceConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(req, 17L);

        assertEquals(new BigDecimal("8.8"), existing.getInputCostPer1k());
        assertEquals(new BigDecimal("9.9"), existing.getOutputCostPer1k());
    }

    @Test
    void listAll_whenPricingTiersEmpty_dtoTiersRemainsNull() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity entity = new LlmPriceConfigEntity();
        entity.setName("MODEL_TIER_EMPTY");
        Map<String, Object> pricing = new HashMap<>();
        pricing.put("strategy", "TIERED");
        pricing.put("tiers", List.of());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pricing", pricing);
        entity.setMetadata(metadata);

        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(entity));

        List<AdminLlmPriceConfigDTO> result = service.listAll();

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getPricing());
        assertNull(result.get(0).getPricing().getTiers());
    }

    @Test
    void listAll_whenPricingTiersHaveNonPositiveTokens_keepsThoseTiers() {
        LlmPriceConfigRepository repository = mock(LlmPriceConfigRepository.class);
        LlmPriceConfigAdminService service = new LlmPriceConfigAdminService(repository);

        LlmPriceConfigEntity entity = new LlmPriceConfigEntity();
        entity.setName("MODEL_TIER_NON_POSITIVE");

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("upToTokens", 0L);
        Map<String, Object> tier2 = new HashMap<>();
        tier2.put("upToTokens", -5L);
        Map<String, Object> tier3 = new HashMap<>();
        tier3.put("upToTokens", 120L);

        Map<String, Object> pricing = new HashMap<>();
        pricing.put("strategy", "TIERED");
        pricing.put("tiers", List.of(tier1, tier2, tier3));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pricing", pricing);
        entity.setMetadata(metadata);

        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(entity));

        List<AdminLlmPriceConfigDTO> result = service.listAll();

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getPricing());
        assertNotNull(result.get(0).getPricing().getTiers());
        assertEquals(3, result.get(0).getPricing().getTiers().size());
        assertEquals(0L, result.get(0).getPricing().getTiers().get(0).getUpToTokens());
        assertEquals(-5L, result.get(0).getPricing().getTiers().get(1).getUpToTokens());
        assertEquals(120L, result.get(0).getPricing().getTiers().get(2).getUpToTokens());
    }

    @Test
    void privateNormalizeAndAsStringAndAsLong_branchesCovered() throws Exception {
        assertNull(invokeStatic("normalize", new Class[]{String.class}, new Object[]{null}));
        assertNull(invokeStatic("normalize", new Class[]{String.class}, new Object[]{"   "}));
        assertEquals("abc", invokeStatic("normalize", new Class[]{String.class}, new Object[]{" abc "}));

        assertNull(invokeStatic("asString", new Class[]{Object.class}, new Object[]{null}));
        assertNull(invokeStatic("asString", new Class[]{Object.class}, new Object[]{"   "}));
        assertEquals("123", invokeStatic("asString", new Class[]{Object.class}, new Object[]{123}));

        assertNull(invokeStatic("asLong", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(9L, invokeStatic("asLong", new Class[]{Object.class}, new Object[]{9}));
        assertEquals(11L, invokeStatic("asLong", new Class[]{Object.class}, new Object[]{"11"}));
        assertNull(invokeStatic("asLong", new Class[]{Object.class}, new Object[]{"bad"}));
    }

    @Test
    void privateAsBigDecimal_andPutIfNotNull_branchesCovered() throws Exception {
        assertNull(invokeStatic("asBigDecimal", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(new BigDecimal("1.2"), invokeStatic("asBigDecimal", new Class[]{Object.class}, new Object[]{new BigDecimal("1.2")}));
        assertEquals(BigDecimal.valueOf(2), invokeStatic("asBigDecimal", new Class[]{Object.class}, new Object[]{2}));
        assertEquals(BigDecimal.valueOf(3.5d), invokeStatic("asBigDecimal", new Class[]{Object.class}, new Object[]{3.5d}));
        assertNull(invokeStatic("asBigDecimal", new Class[]{Object.class}, new Object[]{"   "}));
        assertEquals(new BigDecimal("7.8"), invokeStatic("asBigDecimal", new Class[]{Object.class}, new Object[]{"7.8"}));
        assertNull(invokeStatic("asBigDecimal", new Class[]{Object.class}, new Object[]{"bad"}));
        assertNull(invokeStatic("asBigDecimal", new Class[]{Object.class}, new Object[]{new Object()}));

        invokeStatic("putIfNotNull", new Class[]{Map.class, String.class, Object.class}, new Object[]{null, "k", 1});

        Map<String, Object> map = new HashMap<>();
        invokeStatic("putIfNotNull", new Class[]{Map.class, String.class, Object.class}, new Object[]{map, null, 1});
        assertTrue(map.isEmpty());

        invokeStatic("putIfNotNull", new Class[]{Map.class, String.class, Object.class}, new Object[]{map, "k", null});
        assertTrue(map.isEmpty());

        invokeStatic("putIfNotNull", new Class[]{Map.class, String.class, Object.class}, new Object[]{map, "k", 2});
        assertEquals(2, map.get("k"));
    }

    private static Object invokeStatic(String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method method = LlmPriceConfigAdminService.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
