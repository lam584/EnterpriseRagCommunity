package com.example.EnterpriseRagCommunity.service.moderation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.RiskLabelingEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.RiskLabelingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RiskLabelingService {

    private final RiskLabelingRepository riskLabelingRepository;
    private final TagsRepository tagsRepository;

    @Transactional(readOnly = true)
    public List<String> getRiskTagSlugs(ContentType targetType, Long targetId) {
        if (targetType == null || targetId == null) return List.of();
        List<RiskLabelingEntity> rows = riskLabelingRepository.findAllByTargetTypeAndTargetId(targetType, targetId);
        if (rows.isEmpty()) return List.of();
        return mapToSlugs(rows);
    }

    @Transactional
    public void replaceRiskTags(ContentType targetType, Long targetId, Source source, List<String> rawTags, BigDecimal confidence, boolean clearAllExisting) {
        if (targetType == null) throw new IllegalArgumentException("targetType 不能为空");
        if (targetId == null) throw new IllegalArgumentException("targetId 不能为空");
        if (source == null) throw new IllegalArgumentException("source 不能为空");

        List<String> normalized = normalizeSlugs(rawTags);
        LocalDateTime now = LocalDateTime.now();

        if (clearAllExisting) {
            riskLabelingRepository.deleteAllByTargetTypeAndTargetId(targetType, targetId);
        } else {
            riskLabelingRepository.deleteAllByTargetTypeAndTargetIdAndSource(targetType, targetId, source);
        }

        if (normalized.isEmpty()) return;

        for (String slug : normalized) {
            TagsEntity tag = ensureRiskTagExists(slug);
            RiskLabelingEntity rl = new RiskLabelingEntity();
            rl.setTargetType(targetType);
            rl.setTargetId(targetId);
            rl.setTagId(tag.getId());
            rl.setSource(source);
            rl.setConfidence(confidence);
            rl.setCreatedAt(now);
            riskLabelingRepository.save(rl);
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, List<String>> getRiskTagSlugsByTargets(ContentType targetType, Collection<Long> targetIds) {
        if (targetType == null || targetIds == null || targetIds.isEmpty()) return Map.of();
        List<Long> ids = targetIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        List<RiskLabelingEntity> rows = riskLabelingRepository.findAllByTargetTypeAndTargetIdIn(targetType, ids);
        if (rows.isEmpty()) return Map.of();

        Map<Long, List<RiskLabelingEntity>> byTarget = new HashMap<>();
        for (RiskLabelingEntity rl : rows) {
            if (rl == null || rl.getTargetId() == null) continue;
            byTarget.computeIfAbsent(rl.getTargetId(), k -> new ArrayList<>()).add(rl);
        }

        Map<Long, List<String>> out = new HashMap<>();
        for (Map.Entry<Long, List<RiskLabelingEntity>> e : byTarget.entrySet()) {
            out.put(e.getKey(), mapToSlugs(e.getValue()));
        }
        return out;
    }

    private List<String> mapToSlugs(List<RiskLabelingEntity> rows) {
        LinkedHashSet<Long> tagIds = new LinkedHashSet<>();
        for (RiskLabelingEntity rl : rows) {
            if (rl != null && rl.getTagId() != null) tagIds.add(rl.getTagId());
        }
        if (tagIds.isEmpty()) return List.of();

        Map<Long, String> slugById = new HashMap<>();
        tagsRepository.findAllById(tagIds).forEach(t -> slugById.put(t.getId(), t.getSlug()));

        LinkedHashSet<String> slugs = new LinkedHashSet<>();
        for (Long id : tagIds) {
            String s = slugById.get(id);
            if (s != null && !s.isBlank()) slugs.add(s);
        }
        return new ArrayList<>(slugs);
    }

    private TagsEntity ensureRiskTagExists(String slug) {
        TagsEntity exists = tagsRepository.findByTenantIdAndTypeAndSlug(1L, TagType.RISK, slug).orElse(null);
        if (exists != null) return exists;

        TagsEntity created = new TagsEntity();
        created.setTenantId(1L);
        created.setType(TagType.RISK);
        created.setName(slug.length() > 64 ? slug.substring(0, 64) : slug);
        created.setSlug(slug);
        created.setDescription(null);
        created.setIsSystem(false);
        created.setIsActive(true);
        created.setCreatedAt(LocalDateTime.now());
        return tagsRepository.save(created);
    }

    static List<String> normalizeSlugs(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String raw : rawTags) {
            String slug = normalizeSlug(raw);
            if (slug != null && !slug.isBlank()) set.add(slug);
        }
        return new ArrayList<>(set);
    }

    static String normalizeSlug(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isBlank()) return null;
        s = s.toLowerCase(Locale.ROOT);
        s = s.replace('_', '-');
        s = s.replaceAll("[^\\p{L}\\p{N}\\-\\s]+", "");
        s = s.replaceAll("\\s+", "-");
        s = s.replaceAll("-{2,}", "-");
        s = s.replaceAll("^-+|-+$", "");
        if (s.length() > 96) s = s.substring(0, 96);
        s = s.replaceAll("^-+|-+$", "");
        return s.isBlank() ? null : s;
    }
}
