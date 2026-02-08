package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminModerationCostRecordDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminModerationCostRecordsResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenMetricsResponseDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmDecisionsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmDecisionsRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ModerationCostRecordsService {

    private static final String ENV_DEFAULT = "default";

    private final ModerationLlmDecisionsRepository decisionsRepository;
    private final TokenCostMetricsService tokenCostMetricsService;
    private final LlmModelRepository llmModelRepository;
    private final LlmPriceConfigRepository llmPriceConfigRepository;

    @Transactional(readOnly = true)
    public AdminModerationCostRecordsResponseDTO list(LocalDateTime start, LocalDateTime end, String modelLike, Integer page, Integer pageSize) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime s = start == null ? now.minusDays(7) : start;
        LocalDateTime e = end == null ? now : end;
        if (s.isAfter(e)) {
            LocalDateTime t = s;
            s = e;
            e = t;
        }

        LocalDateTime from = s;
        LocalDateTime to = e;

        int p = page == null ? 1 : Math.max(1, page);
        int ps = pageSize == null ? 20 : Math.min(200, Math.max(1, pageSize));

        Specification<ModerationLlmDecisionsEntity> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.between(root.get("decidedAt"), from, to));
            if (modelLike != null && !modelLike.trim().isBlank()) {
                preds.add(cb.like(root.get("model"), "%" + modelLike.trim() + "%"));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };

        PageRequest pr = PageRequest.of(p - 1, ps, Sort.by(Sort.Direction.DESC, "decidedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<ModerationLlmDecisionsEntity> pg = decisionsRepository.findAll(spec, pr);

        Set<String> models = new HashSet<>();
        for (ModerationLlmDecisionsEntity d : pg.getContent()) {
            String m = normalizeModel(d == null ? null : d.getModel());
            if (m != null) models.add(m);
        }
        Map<String, PriceInfo> priceByModel = resolvePrices(models);

        List<AdminModerationCostRecordDTO> content = new ArrayList<>();
        for (ModerationLlmDecisionsEntity d : pg.getContent()) {
            if (d == null) continue;
            String m = normalizeModel(d.getModel());
            long in = d.getTokensIn() == null ? 0L : Math.max(0, d.getTokensIn().longValue());
            long out = d.getTokensOut() == null ? 0L : Math.max(0, d.getTokensOut().longValue());
            long totalTokens = in + out;

            PriceInfo price = m == null ? null : priceByModel.get(m);
            BigDecimal cost = TokenCostCalculator.computeCost(
                    price == null ? null : price.pricing(),
                    LlmPricing.Mode.DEFAULT,
                    in,
                    out
            );

            content.add(new AdminModerationCostRecordDTO(
                    d.getId(),
                    d.getContentType(),
                    d.getContentId(),
                    d.getVerdict(),
                    m == null ? "unknown" : m,
                    d.getTokensIn(),
                    d.getTokensOut(),
                    totalTokens,
                    cost,
                    price == null || !LlmPricing.isConfiguredForMode(price.pricing(), LlmPricing.Mode.DEFAULT),
                    d.getDecidedAt()
            ));
        }

        AdminTokenMetricsResponseDTO totals = tokenCostMetricsService.query(s, e, TokenCostMetricsService.TokenMetricsSource.MODERATION);
        return new AdminModerationCostRecordsResponseDTO(
                totals.getStart(),
                totals.getEnd(),
                totals.getCurrency(),
                totals.getTotalTokens(),
                totals.getTotalCost(),
                content,
                pg.getTotalPages(),
                pg.getTotalElements(),
                p,
                ps
        );
    }

    private Map<String, PriceInfo> resolvePrices(Collection<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) return Map.of();

        Map<String, PriceInfo> out = new HashMap<>();
        List<LlmModelEntity> modelRows = llmModelRepository.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(
                ENV_DEFAULT,
                modelNames
        );

        Map<String, Long> modelToPriceId = new HashMap<>();
        Set<Long> priceIds = new HashSet<>();
        for (LlmModelEntity m : modelRows) {
            if (m == null) continue;
            String name = normalizeModel(m.getModelName());
            if (name == null) continue;
            if (modelToPriceId.containsKey(name)) continue;
            Long pid = m.getPriceConfigId();
            if (pid == null) continue;
            modelToPriceId.put(name, pid);
            priceIds.add(pid);
        }

        if (!priceIds.isEmpty()) {
            List<LlmPriceConfigEntity> pcs = llmPriceConfigRepository.findByIdIn(priceIds);
            Map<Long, LlmPriceConfigEntity> byId = new HashMap<>();
            for (LlmPriceConfigEntity pc : pcs) {
                if (pc == null || pc.getId() == null) continue;
                byId.put(pc.getId(), pc);
            }
            for (Map.Entry<String, Long> en : modelToPriceId.entrySet()) {
                LlmPriceConfigEntity pc = byId.get(en.getValue());
                if (pc == null) continue;
                out.put(en.getKey(), new PriceInfo(
                        normalizeCurrency(pc.getCurrency()),
                        resolvePricing(pc)
                ));
            }
        }

        Set<String> missing = new HashSet<>();
        for (String m : modelNames) {
            String name = normalizeModel(m);
            if (name == null) continue;
            if (!out.containsKey(name)) missing.add(name);
        }

        if (!missing.isEmpty()) {
            List<LlmPriceConfigEntity> pcs = llmPriceConfigRepository.findByNameIn(missing);
            for (LlmPriceConfigEntity pc : pcs) {
                if (pc == null) continue;
                String name = normalizeModel(pc.getName());
                if (name == null) continue;
                out.putIfAbsent(name, new PriceInfo(
                        normalizeCurrency(pc.getCurrency()),
                        resolvePricing(pc)
                ));
            }
        }

        return out;
    }

    private static LlmPricing.Config resolvePricing(LlmPriceConfigEntity pc) {
        if (pc == null) return null;
        LlmPricing.Config meta = LlmPricing.fromMetadata(pc.getMetadata());
        if (meta != null) return meta;
        return LlmPricing.fromLegacy(pc.getInputCostPer1k(), pc.getOutputCostPer1k());
    }

    private static String normalizeModel(String model) {
        if (model == null) return null;
        String s = model.trim();
        return s.isBlank() ? null : s;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null) return null;
        String s = currency.trim();
        return s.isBlank() ? null : s.toUpperCase();
    }

    private record PriceInfo(
            String currency,
            LlmPricing.Config pricing
    ) {
    }
}
