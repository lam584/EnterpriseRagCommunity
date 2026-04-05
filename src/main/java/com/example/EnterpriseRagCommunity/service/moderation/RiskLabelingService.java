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

    public record RiskTagItem(String slug, String name) {
    }

    @Transactional(readOnly = true)
    public List<String> getRiskTagSlugs(ContentType targetType, Long targetId) {
        if (targetType == null || targetId == null) return List.of();
        List<RiskLabelingEntity> rows = riskLabelingRepository.findAllByTargetTypeAndTargetId(targetType, targetId);
        if (rows.isEmpty()) return List.of();
        return mapToSlugs(rows);
    }

    @Transactional(readOnly = true)
    public List<RiskTagItem> getRiskTagItems(ContentType targetType, Long targetId) {
        if (targetType == null || targetId == null) return List.of();
        List<RiskLabelingEntity> rows = riskLabelingRepository.findAllByTargetTypeAndTargetId(targetType, targetId);
        if (rows.isEmpty()) return List.of();
        return mapToItems(rows);
    }

    @Transactional
    public void replaceRiskTags(ContentType targetType, Long targetId, Source source, List<String> rawTags, BigDecimal confidence, boolean clearAllExisting) {
        if (targetType == null) throw new IllegalArgumentException("targetType 不能为空");
        if (targetId == null) throw new IllegalArgumentException("targetId 不能为空");
        if (source == null) throw new IllegalArgumentException("source 不能为空");

        List<String> inputs = rawTags == null ? List.of() : rawTags;
        LocalDateTime now = LocalDateTime.now();

        if (clearAllExisting) {
            riskLabelingRepository.deleteAllByTargetTypeAndTargetId(targetType, targetId);
        } else {
            riskLabelingRepository.deleteAllByTargetTypeAndTargetIdAndSource(targetType, targetId, source);
        }

        if (inputs.isEmpty()) return;

        List<TagsEntity> activeRiskTags;
        try {
            activeRiskTags = tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK);
        } catch (Exception e) {
            activeRiskTags = List.of();
        }
        if (activeRiskTags.isEmpty()) return;

        Map<String, TagsEntity> byName = new HashMap<>();
        Map<String, TagsEntity> bySlug = new HashMap<>();

        for (int pass = 0; pass < 2; pass++) {
            for (TagsEntity t : activeRiskTags) {
                if (t == null || !Boolean.TRUE.equals(t.getIsActive())) continue;
                boolean systemFirst = (t.getTenantId() == null);
                if ((pass == 0) != systemFirst) continue;

                String name = t.getName();
                if (name != null) {
                    String k = name.trim();
                    if (!k.isBlank()) byName.putIfAbsent(k, t);
                }
                String slug = t.getSlug();
                if (slug != null) {
                    String k = slug.trim().toLowerCase(Locale.ROOT);
                    if (!k.isBlank()) bySlug.putIfAbsent(k, t);
                }
            }
        }

        for (String raw : inputs) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.isBlank()) continue;

            TagsEntity tag = byName.get(t);
            if (tag == null) tag = bySlug.get(t.toLowerCase(Locale.ROOT));
            if (tag == null) {
                String ns = normalizeSlug(t);
                if (ns != null) tag = bySlug.get(ns);
            }
            if (tag == null) continue;

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
        Map<Long, List<RiskLabelingEntity>> byTarget = groupByTarget(targetType, targetIds);
        if (byTarget.isEmpty()) return Map.of();

        Map<Long, List<String>> out = new HashMap<>();
        for (Map.Entry<Long, List<RiskLabelingEntity>> e : byTarget.entrySet()) {
            out.put(e.getKey(), mapToSlugs(e.getValue()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<RiskTagItem>> getRiskTagItemsByTargets(ContentType targetType, Collection<Long> targetIds) {
        Map<Long, List<RiskLabelingEntity>> byTarget = groupByTarget(targetType, targetIds);
        if (byTarget.isEmpty()) return Map.of();

        Map<Long, List<RiskTagItem>> out = new HashMap<>();
        for (Map.Entry<Long, List<RiskLabelingEntity>> e : byTarget.entrySet()) {
            out.put(e.getKey(), mapToItems(e.getValue()));
        }
        return out;
    }

    private Map<Long, List<RiskLabelingEntity>> groupByTarget(ContentType targetType, Collection<Long> targetIds) {
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
        return byTarget;
    }

    private List<String> mapToSlugs(List<RiskLabelingEntity> rows) {
        LinkedHashSet<Long> tagIds = collectDistinctTagIds(rows);
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

    private List<RiskTagItem> mapToItems(List<RiskLabelingEntity> rows) {
        LinkedHashSet<Long> tagIds = collectDistinctTagIds(rows);
        if (tagIds.isEmpty()) return List.of();

        Map<Long, TagsEntity> byId = new HashMap<>();
        tagsRepository.findAllById(tagIds).forEach(t -> {
            if (t != null && t.getId() != null) byId.put(t.getId(), t);
        });

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RiskTagItem> out = new ArrayList<>();
        for (Long id : tagIds) {
            TagsEntity t = byId.get(id);
            if (t == null) continue;
            String slug = t.getSlug();
            if (slug == null || slug.isBlank()) continue;
            if (!seen.add(slug)) continue;
            String name = t.getName();
            if (name == null || name.isBlank()) name = slug;
            out.add(new RiskTagItem(slug, name));
        }
        return out;
    }

    private static LinkedHashSet<Long> collectDistinctTagIds(List<RiskLabelingEntity> rows) {
        LinkedHashSet<Long> tagIds = new LinkedHashSet<>();
        if (rows == null) {
            return tagIds;
        }
        for (RiskLabelingEntity row : rows) {
            if (row != null && row.getTagId() != null) {
                tagIds.add(row.getTagId());
            }
        }
        return tagIds;
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
