package com.example.EnterpriseRagCommunity.service.ai;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.EnterpriseRagCommunity.dto.ai.SupportedLanguageDTO;
import com.example.EnterpriseRagCommunity.entity.ai.SupportedLanguageEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SupportedLanguageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupportedLanguageService {

    public static final String DEFAULT_LANGUAGE_CODE = "zh-CN";

    private final SupportedLanguageRepository supportedLanguageRepository;

    public List<SupportedLanguageDTO> listActive() {
        return supportedLanguageRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(SupportedLanguageService::toDto)
                .toList();
    }

    public List<String> listActiveLanguageCodes() {
        return supportedLanguageRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(SupportedLanguageEntity::getLanguageCode)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    public Optional<SupportedLanguageEntity> findByCode(String code) {
        if (!StringUtils.hasText(code)) return Optional.empty();
        return supportedLanguageRepository.findByLanguageCode(code.trim());
    }

    public Optional<SupportedLanguageEntity> findByDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) return Optional.empty();
        return supportedLanguageRepository.findByDisplayName(displayName.trim());
    }

    public String normalizeToLanguageCode(String raw) {
        if (!StringUtils.hasText(raw)) return DEFAULT_LANGUAGE_CODE;
        String t = raw.trim();
        if (!StringUtils.hasText(t)) return DEFAULT_LANGUAGE_CODE;

        String lower = t.toLowerCase(Locale.ROOT);
        if ("zh".equals(lower)) return DEFAULT_LANGUAGE_CODE;
        if ("zh-cn".equals(lower) || "zh_cn".equals(lower) || "zh-hans".equals(lower) || "zh_hans".equals(lower)) return DEFAULT_LANGUAGE_CODE;

        if (findByCode(t).isPresent()) return t;
        return findByDisplayName(t).map(SupportedLanguageEntity::getLanguageCode).orElse(t);
    }

    public SupportedLanguageDTO adminUpsert(SupportedLanguageDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");
        String code = normalizeRequired(payload.getLanguageCode(), "languageCode");
        String displayName = normalizeRequired(payload.getDisplayName(), "displayName");
        String nativeName = normalizeOptional(payload.getNativeName());
        Integer sortOrder = payload.getSortOrder();

        SupportedLanguageEntity e = supportedLanguageRepository.findByLanguageCode(code).orElseGet(() -> {
            SupportedLanguageEntity ne = new SupportedLanguageEntity();
            ne.setLanguageCode(code);
            ne.setIsActive(true);
            int nextSortOrder = supportedLanguageRepository.findTopByIsActiveTrueOrderBySortOrderDescIdDesc()
                    .map(x -> (x.getSortOrder() == null ? 0 : x.getSortOrder()) + 1)
                    .orElse(1);
            ne.setSortOrder(nextSortOrder);
            return ne;
        });

        e.setDisplayName(displayName);
        e.setNativeName(nativeName);
        if (sortOrder != null) e.setSortOrder(sortOrder);
        e.setIsActive(true);
        e = supportedLanguageRepository.save(e);
        return toDto(e);
    }

    public SupportedLanguageDTO adminUpdate(String originalCode, SupportedLanguageDTO payload) {
        String oldCode = normalizeRequired(originalCode, "languageCode");
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        SupportedLanguageEntity e = supportedLanguageRepository.findByLanguageCode(oldCode)
                .orElseThrow(() -> new IllegalArgumentException("语言不存在: " + oldCode));

        String nextCode = normalizeRequired(payload.getLanguageCode(), "languageCode");
        String displayName = normalizeRequired(payload.getDisplayName(), "displayName");
        String nativeName = normalizeOptional(payload.getNativeName());
        Integer sortOrder = payload.getSortOrder();

        if (!oldCode.equals(nextCode)) {
            SupportedLanguageEntity other = supportedLanguageRepository.findByLanguageCode(nextCode).orElse(null);
            if (other != null && other.getId() != null && !other.getId().equals(e.getId())) {
                throw new IllegalArgumentException("languageCode 已存在: " + nextCode);
            }
            e.setLanguageCode(nextCode);
        }

        e.setDisplayName(displayName);
        e.setNativeName(nativeName);
        if (sortOrder != null) e.setSortOrder(sortOrder);
        e.setIsActive(true);
        e = supportedLanguageRepository.save(e);
        return toDto(e);
    }

    public void adminDeactivate(String code) {
        String c = normalizeRequired(code, "languageCode");
        SupportedLanguageEntity e = supportedLanguageRepository.findByLanguageCode(c)
                .orElseThrow(() -> new IllegalArgumentException("语言不存在: " + c));
        e.setIsActive(false);
        supportedLanguageRepository.save(e);
    }

    private static SupportedLanguageDTO toDto(SupportedLanguageEntity e) {
        SupportedLanguageDTO dto = new SupportedLanguageDTO();
        dto.setLanguageCode(e.getLanguageCode());
        dto.setDisplayName(e.getDisplayName());
        dto.setNativeName(e.getNativeName());
        dto.setSortOrder(e.getSortOrder());
        return dto;
    }

    private static String normalizeRequired(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) throw new IllegalArgumentException(fieldName + " 不能为空");
        String t = raw.trim();
        if (!StringUtils.hasText(t)) throw new IllegalArgumentException(fieldName + " 不能为空");
        return t;
    }

    private static String normalizeOptional(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String t = raw.trim();
        return t.isBlank() ? null : t;
    }
}
