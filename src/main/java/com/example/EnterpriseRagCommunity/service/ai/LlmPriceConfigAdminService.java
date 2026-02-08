package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigPricingDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigPricingTierDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigUpsertRequest;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmPriceConfigAdminService {

    private final LlmPriceConfigRepository llmPriceConfigRepository;
    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);

    @Transactional(readOnly = true)
    public List<AdminLlmPriceConfigDTO> listAll() {
        List<LlmPriceConfigEntity> rows = llmPriceConfigRepository.findAll(Sort.by(Sort.Direction.ASC, "name", "id"));
        List<AdminLlmPriceConfigDTO> out = new ArrayList<>();
        for (LlmPriceConfigEntity e : rows) {
            if (e == null) continue;
            out.add(toDto(e));
        }
        return out;
    }

    @Transactional
    public AdminLlmPriceConfigDTO upsert(AdminLlmPriceConfigUpsertRequest req, Long actorUserId) {
        if (req == null) throw new IllegalArgumentException("req 不能为空");
        String name = normalize(req.getName());
        if (name == null) throw new IllegalArgumentException("name 不能为空");

        LocalDateTime now = LocalDateTime.now();
        LlmPriceConfigEntity e = llmPriceConfigRepository.findByName(name).orElse(null);
        boolean isNew = (e == null);
        if (isNew) {
            e = new LlmPriceConfigEntity();
            e.setName(name);
            e.setCurrency("CNY");
            e.setCreatedAt(now);
            e.setCreatedBy(actorUserId);
        }

        String currency = normalize(req.getCurrency());
        if (currency != null) e.setCurrency(currency.toUpperCase());
        if (req.getInputCostPer1k() != null) e.setInputCostPer1k(req.getInputCostPer1k());
        if (req.getOutputCostPer1k() != null) e.setOutputCostPer1k(req.getOutputCostPer1k());

        AdminLlmPriceConfigPricingDTO pricing = req.getPricing();
        if (pricing != null) {
            Map<String, Object> meta = e.getMetadata() == null ? new HashMap<>() : new HashMap<>(e.getMetadata());
            Map<String, Object> pmap = new HashMap<>();
            putIfNotNull(pmap, "strategy", normalize(pricing.getStrategy()));
            putIfNotNull(pmap, "unit", normalize(pricing.getUnit()));
            putIfNotNull(pmap, "defaultInputCostPerUnit", pricing.getDefaultInputCostPerUnit());
            putIfNotNull(pmap, "defaultOutputCostPerUnit", pricing.getDefaultOutputCostPerUnit());
            putIfNotNull(pmap, "nonThinkingInputCostPerUnit", pricing.getNonThinkingInputCostPerUnit());
            putIfNotNull(pmap, "nonThinkingOutputCostPerUnit", pricing.getNonThinkingOutputCostPerUnit());
            putIfNotNull(pmap, "thinkingInputCostPerUnit", pricing.getThinkingInputCostPerUnit());
            putIfNotNull(pmap, "thinkingOutputCostPerUnit", pricing.getThinkingOutputCostPerUnit());

            List<AdminLlmPriceConfigPricingTierDTO> tiers = pricing.getTiers();
            if (tiers != null && !tiers.isEmpty()) {
                List<Map<String, Object>> tmaps = new ArrayList<>();
                for (AdminLlmPriceConfigPricingTierDTO t : tiers) {
                    if (t == null) continue;
                    Long upTo = t.getUpToTokens();
                    if (upTo == null || upTo <= 0) continue;
                    Map<String, Object> tmap = new HashMap<>();
                    tmap.put("upToTokens", upTo);
                    putIfNotNull(tmap, "inputCostPerUnit", t.getInputCostPerUnit());
                    putIfNotNull(tmap, "outputCostPerUnit", t.getOutputCostPerUnit());
                    tmaps.add(tmap);
                }
                if (!tmaps.isEmpty()) {
                    pmap.put("tiers", tmaps);
                }
            }

            meta.put("pricing", pmap);
            e.setMetadata(meta);

            String strategy = normalize(pricing.getStrategy());
            String unit = normalize(pricing.getUnit());
            if (strategy == null || "FLAT".equalsIgnoreCase(strategy)) {
                BigDecimal in = pricing.getDefaultInputCostPerUnit();
                BigDecimal out = pricing.getDefaultOutputCostPerUnit();
                if ("PER_1M".equalsIgnoreCase(unit)) {
                    e.setInputCostPer1k(in == null ? null : in.divide(ONE_THOUSAND, 8, RoundingMode.HALF_UP));
                    BigDecimal outUnit = out == null ? null : out.divide(ONE_THOUSAND, 8, RoundingMode.HALF_UP);
                    e.setOutputCostPer1k(outUnit);
                } else if ("PER_1K".equalsIgnoreCase(unit) || unit == null) {
                    e.setInputCostPer1k(in);
                    e.setOutputCostPer1k(out);
                }
            } else if ("TIERED".equalsIgnoreCase(strategy)) {
                e.setInputCostPer1k(null);
                e.setOutputCostPer1k(null);
            }
        }

        e.setUpdatedAt(now);
        e.setUpdatedBy(actorUserId);
        e = llmPriceConfigRepository.save(e);
        return toDto(e);
    }

    private static AdminLlmPriceConfigDTO toDto(LlmPriceConfigEntity e) {
        AdminLlmPriceConfigDTO dto = new AdminLlmPriceConfigDTO();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setCurrency(e.getCurrency());
        dto.setInputCostPer1k(e.getInputCostPer1k());
        dto.setOutputCostPer1k(e.getOutputCostPer1k());
        dto.setPricing(parsePricing(e.getMetadata()));
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    @SuppressWarnings("unchecked")
    private static AdminLlmPriceConfigPricingDTO parsePricing(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        Object pricing = metadata.get("pricing");
        if (!(pricing instanceof Map<?, ?> pmap)) return null;

        AdminLlmPriceConfigPricingDTO dto = new AdminLlmPriceConfigPricingDTO();
        dto.setStrategy(asString(pmap.get("strategy")));
        dto.setUnit(asString(pmap.get("unit")));
        dto.setDefaultInputCostPerUnit(asBigDecimal(pmap.get("defaultInputCostPerUnit")));
        dto.setDefaultOutputCostPerUnit(asBigDecimal(pmap.get("defaultOutputCostPerUnit")));
        dto.setNonThinkingInputCostPerUnit(asBigDecimal(pmap.get("nonThinkingInputCostPerUnit")));
        dto.setNonThinkingOutputCostPerUnit(asBigDecimal(pmap.get("nonThinkingOutputCostPerUnit")));
        dto.setThinkingInputCostPerUnit(asBigDecimal(pmap.get("thinkingInputCostPerUnit")));
        dto.setThinkingOutputCostPerUnit(asBigDecimal(pmap.get("thinkingOutputCostPerUnit")));

        Object tiers = pmap.get("tiers");
        if (tiers instanceof List<?> list && !list.isEmpty()) {
            List<AdminLlmPriceConfigPricingTierDTO> out = new ArrayList<>();
            for (Object t : list) {
                if (!(t instanceof Map<?, ?> tm)) continue;
                Long upTo = asLong(tm.get("upToTokens"));
                if (upTo == null) continue;
                AdminLlmPriceConfigPricingTierDTO td = new AdminLlmPriceConfigPricingTierDTO();
                td.setUpToTokens(upTo);
                td.setInputCostPerUnit(asBigDecimal(tm.get("inputCostPerUnit")));
                td.setOutputCostPerUnit(asBigDecimal(tm.get("outputCostPerUnit")));
                out.add(td);
            }
            if (!out.isEmpty()) dto.setTiers(out);
        }
        return dto;
    }

    private static String normalize(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isBlank() ? null : s;
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal asBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) {
            if (v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long) {
                return BigDecimal.valueOf(n.longValue());
            }
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return new BigDecimal(t);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (m == null || k == null) return;
        if (v == null) return;
        m.put(k, v);
    }
}
