package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiProviderDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProvidersConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderSettingsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderSettingsRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiProvidersConfigService {

    public static final String KEY_CONFIG_JSON = "ai.providers.config.json";
    private static final String MASK = "******";
    private static final String ENV_DEFAULT = "default";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;
    private final LlmProviderRepository llmProviderRepository;
    private final LlmProviderSettingsRepository llmProviderSettingsRepository;
    private final LlmModelRepository llmModelRepository;
    private final LlmSecretsCryptoService llmSecretsCryptoService;
    private final SystemConfigurationService systemConfigurationService;

    @Transactional(readOnly = true)
    public AiProvidersConfigDTO getAdminConfig() {
        AiProvidersConfigDTO cfg = loadAdminConfigOrLegacyOrDefault(ENV_DEFAULT);
        return maskSecrets(cfg);
    }

    @Transactional
    public AiProvidersConfigDTO updateAdminConfig(AiProvidersConfigDTO payload) {
        return updateAdminConfig(payload, null);
    }

    @Transactional
    public AiProvidersConfigDTO updateAdminConfig(AiProvidersConfigDTO payload, Long actorUserId) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        String env = ENV_DEFAULT;
        LocalDateTime now = LocalDateTime.now();

        List<LlmProviderEntity> existing = llmProviderRepository.findByEnvOrderByPriorityAscIdAsc(env);
        Map<String, LlmProviderEntity> oldByProviderId = new LinkedHashMap<>();
        for (LlmProviderEntity e : existing) {
            if (e == null) continue;
            String pid = toNonBlank(e.getProviderId());
            if (pid != null) oldByProviderId.put(pid, e);
        }

        List<AiProviderDTO> incoming = payload.getProviders() == null ? List.of() : payload.getProviders();
        Set<String> nextProviderIds = new HashSet<>();
        List<LlmProviderEntity> toSave = new ArrayList<>();

        int ord = 0;
        for (AiProviderDTO p : incoming) {
            if (p == null) continue;
            String providerId = toNonBlank(p.getId());
            if (providerId == null) continue;

            nextProviderIds.add(providerId);
            LlmProviderEntity e = oldByProviderId.get(providerId);
            boolean isNew = (e == null);
            if (isNew) {
                e = new LlmProviderEntity();
                e.setEnv(env);
                e.setProviderId(providerId);
                e.setCreatedAt(now);
                e.setCreatedBy(actorUserId);
                e.setPriority(ord * 10);
            }

            e.setName(toNonBlank(p.getName()));
            String type = toNonBlank(p.getType());
            if (type == null) type = "OPENAI_COMPAT";
            e.setType(type.trim().toUpperCase(Locale.ROOT));
            e.setBaseUrl(toNonBlank(p.getBaseUrl()));
            e.setDefaultChatModel(toNonBlank(p.getDefaultChatModel()));
            e.setDefaultEmbeddingModel(toNonBlank(p.getDefaultEmbeddingModel()));

            Map<String, Object> meta = e.getMetadata() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(e.getMetadata());
            String defaultRerankModel = toNonBlank(p.getDefaultRerankModel());
            if (defaultRerankModel == null) meta.remove("defaultRerankModel");
            else meta.put("defaultRerankModel", defaultRerankModel);
            String rerankEndpointPath = toNonBlank(p.getRerankEndpointPath());
            if (rerankEndpointPath == null) meta.remove("rerankEndpointPath");
            else meta.put("rerankEndpointPath", rerankEndpointPath);
            if (p.getSupportsVision() == null) meta.remove("supportsVision");
            else meta.put("supportsVision", Boolean.TRUE.equals(p.getSupportsVision()));
            e.setMetadata(meta.isEmpty() ? null : meta);

            e.setConnectTimeoutMs(positiveOrNull(p.getConnectTimeoutMs()));
            e.setReadTimeoutMs(positiveOrNull(p.getReadTimeoutMs()));
            e.setMaxConcurrent(positiveOrNull(p.getMaxConcurrent()));
            e.setEnabled(p.getEnabled() == null || Boolean.TRUE.equals(p.getEnabled()));
            e.setPriority(ord * 10);
            e.setUpdatedAt(now);
            e.setUpdatedBy(actorUserId);

            String apiKeyRaw = p.getApiKey();
            if (apiKeyRaw != null) {
                String apiKeyTrim = apiKeyRaw.trim();
                if (!MASK.equals(apiKeyTrim)) {
                    if (apiKeyTrim.isEmpty()) {
                        e.setApiKeyEncrypted(null);
                    } else {
                        if (!llmSecretsCryptoService.isConfigured()) {
                            throw new IllegalStateException("未配置主密钥，无法保存 apiKey（请配置 app.security.totp.master-key / APP_TOTP_MASTER_KEY）");
                        }
                        e.setApiKeyEncrypted(llmSecretsCryptoService.encryptStringOrNull(apiKeyTrim));
                    }
                }
            }

            Map<String, String> payloadHeaders = p.getExtraHeaders();
            if (payloadHeaders != null) {
                boolean hasReal = hasAnyRealSecretValue(payloadHeaders);
                if (hasReal && !llmSecretsCryptoService.isConfigured()) {
                    throw new IllegalStateException("未配置主密钥，无法保存 extraHeaders（请配置 app.security.totp.master-key / APP_TOTP_MASTER_KEY）");
                }
                if (llmSecretsCryptoService.isConfigured()) {
                    Map<String, String> oldHeaders = e.getExtraHeadersEncrypted() == null ? Map.of() : llmSecretsCryptoService.decryptHeadersOrEmpty(e.getExtraHeadersEncrypted());
                    Map<String, String> merged = mergeHeaders(oldHeaders, payloadHeaders);
                    e.setExtraHeadersEncrypted(merged.isEmpty() ? null : llmSecretsCryptoService.encryptHeadersOrNull(merged));
                }
            }

            toSave.add(e);
            ord++;
        }

        llmProviderRepository.saveAll(toSave);

        for (Map.Entry<String, LlmProviderEntity> en : oldByProviderId.entrySet()) {
            String oldId = en.getKey();
            if (!nextProviderIds.contains(oldId)) {
                llmModelRepository.deleteByEnvAndProviderId(env, oldId);
                llmProviderRepository.deleteByEnvAndProviderId(env, oldId);
            }
        }

        String active = toNonBlank(payload.getActiveProviderId());
        if (active == null || !nextProviderIds.contains(active)) {
            active = firstEnabledProviderId(env);
        }
        upsertSettings(env, active, now, actorUserId);

        persistLegacyMaskedCopy(env);

        return getAdminConfig();
    }

    @Transactional(readOnly = true)
    public ResolvedProvider resolveActiveProvider() {
        return resolveFromDbOrLegacyOrProperties(ENV_DEFAULT, null);
    }

    @Transactional(readOnly = true)
    public ResolvedProvider resolveProvider(String providerId) {
        return resolveFromDbOrLegacyOrProperties(ENV_DEFAULT, providerId);
    }

    @Transactional(readOnly = true)
    public List<String> listEnabledProviderIds() {
        String env = ENV_DEFAULT;
        List<LlmProviderEntity> rows = llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc(env);
        List<String> out = new ArrayList<>();
        if (!rows.isEmpty()) {
            Set<String> seen = new HashSet<>();
            for (LlmProviderEntity p : rows) {
                if (p == null) continue;
                String id = toNonBlank(p.getProviderId());
                if (id == null) continue;
                if (seen.add(id)) out.add(id);
            }
            return out;
        }

        AiProvidersConfigDTO legacy = loadLegacyOrDefault();
        List<AiProviderDTO> providers = legacy == null ? List.of() : (legacy.getProviders() == null ? List.of() : legacy.getProviders());
        Set<String> seen = new HashSet<>();
        for (AiProviderDTO p : providers) {
            if (p == null) continue;
            if (Boolean.FALSE.equals(p.getEnabled())) continue;
            String id = toNonBlank(p.getId());
            if (id == null) continue;
            if (seen.add(id)) out.add(id);
        }
        return out;
    }

    private ResolvedProvider resolveFromDbOrLegacyOrProperties(String env, String providerId) {
        List<LlmProviderEntity> providers = llmProviderRepository.findByEnvOrderByPriorityAscIdAsc(env);
        if (providers.isEmpty()) {
            AiProvidersConfigDTO legacy = loadLegacyOrDefault();
            return resolveFromDto(legacy, providerId);
        }
        return resolveFromDb(env, providerId, providers);
    }

    private ResolvedProvider resolveFromDb(String env, String providerId, List<LlmProviderEntity> providers) {
        String desired = toNonBlank(providerId);
        String activeId = llmProviderSettingsRepository.findById(env).map(LlmProviderSettingsEntity::getActiveProviderId).orElse(null);
        if (desired == null) desired = toNonBlank(activeId);

        LlmProviderEntity selected = null;
        if (desired != null) {
            for (LlmProviderEntity p : providers) {
                if (p == null) continue;
                if (desired.equals(toNonBlank(p.getProviderId()))) {
                    selected = p;
                    break;
                }
            }
        }
        if (selected == null) {
            for (LlmProviderEntity p : providers) {
                if (p == null) continue;
                if (!Boolean.FALSE.equals(p.getEnabled())) {
                    selected = p;
                    break;
                }
            }
        }

        if (selected == null) {
            return defaultResolvedProvider();
        }

        String id = toNonBlank(selected.getProviderId());
        if (id == null) id = "provider";
        String type = toNonBlank(selected.getType());
        if (type == null) type = "OPENAI_COMPAT";
        type = type.trim().toUpperCase(Locale.ROOT);

        String baseUrl = toNonBlank(selected.getBaseUrl());
        if (baseUrl == null) baseUrl = getDefaultBaseUrl();

        String apiKey = null;
        if (selected.getApiKeyEncrypted() != null && llmSecretsCryptoService.isConfigured()) {
            try {
                apiKey = llmSecretsCryptoService.decryptStringOrNull(selected.getApiKeyEncrypted());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "模型来源（providerId=" + id + "）apiKey 解密失败: " + e.getMessage() + "。请确认 app.security.totp.master-key 未变更；或在管理页重新填写并保存该来源的 apiKey。",
                        e
                );
            }
        }
        if (apiKey == null) apiKey = getDefaultApiKey();

        String chatModel = toNonBlank(selected.getDefaultChatModel());
        if (chatModel == null) chatModel = getDefaultModel();
        String embModel = toNonBlank(selected.getDefaultEmbeddingModel());
        if (embModel == null) embModel = "text-embedding-v4";

        Map<String, String> headers = Map.of();
        if (selected.getExtraHeadersEncrypted() != null && llmSecretsCryptoService.isConfigured()) {
            headers = normalizeHeaders(llmSecretsCryptoService.decryptHeadersOrEmpty(selected.getExtraHeadersEncrypted()));
        }

        Integer connectTimeoutMs = selected.getConnectTimeoutMs();
        if (connectTimeoutMs == null || connectTimeoutMs <= 0) connectTimeoutMs = aiProperties.getConnectTimeoutMs();
        Integer readTimeoutMs = selected.getReadTimeoutMs();
        if (readTimeoutMs == null || readTimeoutMs <= 0) readTimeoutMs = aiProperties.getReadTimeoutMs();
        Integer maxConcurrent = selected.getMaxConcurrent();
        if (maxConcurrent != null && maxConcurrent <= 0) maxConcurrent = null;

        Map<String, Object> metadata = selected.getMetadata() == null ? Map.of() : Map.copyOf(selected.getMetadata());
        return new ResolvedProvider(id, type, baseUrl, apiKey, chatModel, embModel, metadata, headers, connectTimeoutMs, readTimeoutMs, maxConcurrent);
    }

    private ResolvedProvider resolveFromDto(AiProvidersConfigDTO cfg, String providerId) {
        String desired = toNonBlank(providerId);
        List<AiProviderDTO> providers = cfg == null ? List.of() : (cfg.getProviders() == null ? List.of() : cfg.getProviders());
        AiProviderDTO selected = null;
        if (desired != null) {
            for (AiProviderDTO p : providers) {
                if (p == null) continue;
                if (desired.equals(toNonBlank(p.getId()))) {
                    selected = p;
                    break;
                }
            }
        }
        if (selected == null) {
            String activeId = cfg == null ? null : cfg.getActiveProviderId();
            String active = toNonBlank(activeId);
            if (active != null) {
                for (AiProviderDTO p : providers) {
                    if (p == null) continue;
                    if (active.equals(toNonBlank(p.getId()))) {
                        selected = p;
                        break;
                    }
                }
            }
        }
        if (selected == null) {
            for (AiProviderDTO p : providers) {
                if (p == null) continue;
                if (!Boolean.FALSE.equals(p.getEnabled())) {
                    selected = p;
                    break;
                }
            }
        }

        if (selected == null) {
            return defaultResolvedProvider();
        }

        String id = toNonBlank(selected.getId());
        if (id == null) id = "provider";
        String type = toNonBlank(selected.getType());
        if (type == null) type = "OPENAI_COMPAT";
        type = type.trim().toUpperCase(Locale.ROOT);

        String baseUrl = toNonBlank(selected.getBaseUrl());
        if (baseUrl == null) baseUrl = getDefaultBaseUrl();

        String apiKey = toNonBlank(selected.getApiKey());
        if (MASK.equals(apiKey)) apiKey = null;
        if (apiKey == null) apiKey = getDefaultApiKey();

        String chatModel = toNonBlank(selected.getDefaultChatModel());
        if (chatModel == null) chatModel = getDefaultModel();
        String embModel = toNonBlank(selected.getDefaultEmbeddingModel());
        if (embModel == null) embModel = "text-embedding-v4";

        Map<String, String> headers = normalizeHeaders(selected.getExtraHeaders());
        Integer connectTimeoutMs = selected.getConnectTimeoutMs();
        if (connectTimeoutMs == null || connectTimeoutMs <= 0) connectTimeoutMs = aiProperties.getConnectTimeoutMs();
        Integer readTimeoutMs = selected.getReadTimeoutMs();
        if (readTimeoutMs == null || readTimeoutMs <= 0) readTimeoutMs = aiProperties.getReadTimeoutMs();
        Integer maxConcurrent = selected.getMaxConcurrent();
        if (maxConcurrent != null && maxConcurrent <= 0) maxConcurrent = null;

        Map<String, Object> metadata = Map.of();
        {
            Map<String, Object> meta = new LinkedHashMap<>();
            String drm = toNonBlank(selected.getDefaultRerankModel());
            if (drm != null) meta.put("defaultRerankModel", drm);
            String rep = toNonBlank(selected.getRerankEndpointPath());
            if (rep != null) meta.put("rerankEndpointPath", rep);
            if (selected.getSupportsVision() != null) meta.put("supportsVision", Boolean.TRUE.equals(selected.getSupportsVision()));
            metadata = meta.isEmpty() ? Map.of() : Map.copyOf(meta);
        }
        return new ResolvedProvider(id, type, baseUrl, apiKey, chatModel, embModel, metadata, headers, connectTimeoutMs, readTimeoutMs, maxConcurrent);
    }

    private ResolvedProvider defaultResolvedProvider() {
        return new ResolvedProvider(
                "default",
                "OPENAI_COMPAT",
                getDefaultBaseUrl(),
                getDefaultApiKey(),
                getDefaultModel(),
                "text-embedding-v4",
                Map.of(),
                Map.of(),
                aiProperties.getConnectTimeoutMs(),
                aiProperties.getReadTimeoutMs(),
                null
        );
    }
    
    private String getDefaultBaseUrl() {
        String val = systemConfigurationService.getConfig("APP_AI_BASE_URL");
        if (val == null || val.isBlank()) val = systemConfigurationService.getConfig("app.ai.base-url");
        if (val != null && !val.isBlank()) return val;
        return aiProperties.getBaseUrl();
    }

    private String getDefaultApiKey() {
        String val = systemConfigurationService.getConfig("APP_AI_API_KEY");
        if (val == null || val.isBlank()) val = systemConfigurationService.getConfig("app.ai.api-key");
        if (val != null && !val.isBlank()) return val;
        return aiProperties.getApiKey();
    }
    
    private String getDefaultModel() {
        String val = systemConfigurationService.getConfig("APP_AI_MODEL");
        if (val == null || val.isBlank()) val = systemConfigurationService.getConfig("app.ai.model");
        if (val != null && !val.isBlank()) return val;
        return aiProperties.getModel();
    }

    private String firstEnabledProviderId(String env) {
        return llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc(env)
                .stream()
                .findFirst()
                .map(LlmProviderEntity::getProviderId)
                .orElse(null);
    }

    private void upsertSettings(String env, String activeProviderId, LocalDateTime now, Long actorUserId) {
        LlmProviderSettingsEntity s = llmProviderSettingsRepository.findById(env).orElse(null);
        if (s == null) {
            s = new LlmProviderSettingsEntity();
            s.setEnv(env);
        }
        s.setActiveProviderId(activeProviderId);
        s.setUpdatedAt(now);
        s.setUpdatedBy(actorUserId);
        llmProviderSettingsRepository.save(s);
    }

    private void persistLegacyMaskedCopy(String env) {
        try {
            AiProvidersConfigDTO cfg = loadAdminConfigFromDb(env);
            String json = objectMapper.writeValueAsString(maskSecrets(cfg));
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
        } catch (Exception e) {
            throw new IllegalStateException("写入兼容配置失败: " + e.getMessage(), e);
        }
    }

    private AiProvidersConfigDTO loadAdminConfigFromDb(String env) {
        AiProvidersConfigDTO out = new AiProvidersConfigDTO();
        out.setActiveProviderId(llmProviderSettingsRepository.findById(env).map(LlmProviderSettingsEntity::getActiveProviderId).orElse(null));

        List<LlmProviderEntity> rows = llmProviderRepository.findByEnvOrderByPriorityAscIdAsc(env);
        List<AiProviderDTO> providers = new ArrayList<>();
        for (LlmProviderEntity e : rows) {
            if (e == null) continue;
            AiProviderDTO p = new AiProviderDTO();
            p.setId(e.getProviderId());
            p.setName(e.getName());
            p.setType(e.getType());
            p.setBaseUrl(e.getBaseUrl());
            p.setApiKey(null);
            p.setDefaultChatModel(e.getDefaultChatModel());
            p.setDefaultEmbeddingModel(e.getDefaultEmbeddingModel());
            Object drm = (e.getMetadata() == null) ? null : e.getMetadata().get("defaultRerankModel");
            if (drm instanceof String s && !s.isBlank()) {
                p.setDefaultRerankModel(s.trim());
            }
            Object rep = (e.getMetadata() == null) ? null : e.getMetadata().get("rerankEndpointPath");
            if (rep instanceof String s && !s.isBlank()) {
                p.setRerankEndpointPath(s.trim());
            }
            Object sv = (e.getMetadata() == null) ? null : e.getMetadata().get("supportsVision");
            if (sv instanceof Boolean b) {
                p.setSupportsVision(b);
            }
            if (e.getExtraHeadersEncrypted() != null && llmSecretsCryptoService.isConfigured()) {
                Map<String, String> hdr = llmSecretsCryptoService.decryptHeadersOrEmpty(e.getExtraHeadersEncrypted());
                p.setExtraHeaders(normalizeHeaders(hdr));
            } else {
                p.setExtraHeaders(Map.of());
            }
            p.setConnectTimeoutMs(e.getConnectTimeoutMs());
            p.setReadTimeoutMs(e.getReadTimeoutMs());
            p.setMaxConcurrent(e.getMaxConcurrent());
            p.setEnabled(e.getEnabled());
            providers.add(p);
        }
        out.setProviders(providers);

        String active = toNonBlank(out.getActiveProviderId());
        if (active == null && !providers.isEmpty()) active = toNonBlank(providers.get(0).getId());
        out.setActiveProviderId(active);
        return out;
    }

    private AiProvidersConfigDTO loadAdminConfigOrLegacyOrDefault(String env) {
        List<LlmProviderEntity> providers = llmProviderRepository.findByEnvOrderByPriorityAscIdAsc(env);
        if (!providers.isEmpty()) return loadAdminConfigFromDb(env);
        return normalize(loadLegacyOrDefault());
    }

    private AiProvidersConfigDTO loadLegacyOrDefault() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) return normalize(defaultConfig());
        try {
            AiProvidersConfigDTO cfg = objectMapper.readValue(json, AiProvidersConfigDTO.class);
            return normalize(cfg);
        } catch (Exception e) {
            return normalize(defaultConfig());
        }
    }

    private AiProvidersConfigDTO defaultConfig() {
        AiProviderDTO p = new AiProviderDTO();
        p.setId("default");
        p.setName("默认");
        p.setType("OPENAI_COMPAT");
        p.setBaseUrl(aiProperties.getBaseUrl());
        p.setApiKey(null);
        p.setDefaultChatModel(aiProperties.getModel());
        p.setDefaultEmbeddingModel("text-embedding-v4");
        p.setExtraHeaders(null);
        p.setConnectTimeoutMs(null);
        p.setReadTimeoutMs(null);
        p.setMaxConcurrent(null);
        p.setEnabled(true);

        AiProvidersConfigDTO cfg = new AiProvidersConfigDTO();
        cfg.setActiveProviderId("default");
        cfg.setProviders(List.of(p));
        return cfg;
    }

    private AiProvidersConfigDTO normalize(AiProvidersConfigDTO in) {
        AiProvidersConfigDTO cfg = in == null ? new AiProvidersConfigDTO() : in;
        List<AiProviderDTO> raw = cfg.getProviders() == null ? List.of() : cfg.getProviders();
        List<AiProviderDTO> out = new ArrayList<>();
        for (AiProviderDTO p : raw) {
            if (p == null) continue;
            AiProviderDTO x = new AiProviderDTO();
            x.setId(toNonBlank(p.getId()));
            x.setName(toNonBlank(p.getName()));
            x.setType(toNonBlank(p.getType()));
            x.setBaseUrl(toNonBlank(p.getBaseUrl()));
            x.setApiKey(toNonBlank(p.getApiKey()));
            if (MASK.equals(x.getApiKey())) x.setApiKey(null);
            x.setDefaultChatModel(toNonBlank(p.getDefaultChatModel()));
            x.setDefaultEmbeddingModel(toNonBlank(p.getDefaultEmbeddingModel()));
            x.setDefaultRerankModel(toNonBlank(p.getDefaultRerankModel()));
            x.setRerankEndpointPath(toNonBlank(p.getRerankEndpointPath()));
            x.setSupportsVision(p.getSupportsVision());
            x.setExtraHeaders(normalizeHeaders(p.getExtraHeaders()));
            x.setConnectTimeoutMs(p.getConnectTimeoutMs() == null || p.getConnectTimeoutMs() <= 0 ? null : p.getConnectTimeoutMs());
            x.setReadTimeoutMs(p.getReadTimeoutMs() == null || p.getReadTimeoutMs() <= 0 ? null : p.getReadTimeoutMs());
            x.setMaxConcurrent(p.getMaxConcurrent() == null || p.getMaxConcurrent() <= 0 ? null : p.getMaxConcurrent());
            x.setEnabled(p.getEnabled() == null || Boolean.TRUE.equals(p.getEnabled()));
            if (x.getId() == null) continue;
            out.add(x);
        }
        cfg.setProviders(out);
        String active = toNonBlank(cfg.getActiveProviderId());
        if (active == null && !out.isEmpty()) active = out.get(0).getId();
        cfg.setActiveProviderId(active);
        return cfg;
    }

    private static AiProvidersConfigDTO maskSecrets(AiProvidersConfigDTO in) {
        if (in == null) return null;
        AiProvidersConfigDTO out = new AiProvidersConfigDTO();
        out.setActiveProviderId(in.getActiveProviderId());
        List<AiProviderDTO> src = in.getProviders() == null ? List.of() : in.getProviders();
        List<AiProviderDTO> dst = new ArrayList<>();
        for (AiProviderDTO p : src) {
            if (p == null) continue;
            AiProviderDTO x = new AiProviderDTO();
            x.setId(p.getId());
            x.setName(p.getName());
            x.setType(p.getType());
            x.setBaseUrl(p.getBaseUrl());
            x.setApiKey(MASK);
            x.setDefaultChatModel(p.getDefaultChatModel());
            x.setDefaultEmbeddingModel(p.getDefaultEmbeddingModel());
            x.setDefaultRerankModel(p.getDefaultRerankModel());
            x.setRerankEndpointPath(p.getRerankEndpointPath());
            x.setSupportsVision(p.getSupportsVision());
            x.setExtraHeaders(maskHeaders(p.getExtraHeaders()));
            x.setConnectTimeoutMs(p.getConnectTimeoutMs());
            x.setReadTimeoutMs(p.getReadTimeoutMs());
            x.setMaxConcurrent(p.getMaxConcurrent());
            x.setEnabled(p.getEnabled());
            dst.add(x);
        }
        out.setProviders(dst);
        return out;
    }

    private static Integer positiveOrNull(Integer v) {
        if (v == null) return null;
        if (v <= 0) return null;
        return v;
    }

    private static boolean hasAnyRealSecretValue(Map<String, String> headers) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String v = toNonBlank(e.getValue());
            if (v == null) continue;
            if (MASK.equals(v)) continue;
            return true;
        }
        return false;
    }

    private static String toNonBlank(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    public record ResolvedProvider(
            String id,
            String type,
            String baseUrl,
            String apiKey,
            String defaultChatModel,
            String defaultEmbeddingModel,
            Map<String, Object> metadata,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Integer maxConcurrent
    ) {
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String k = toNonBlank(e.getKey());
            if (k == null) continue;
            String v = toNonBlank(e.getValue());
            if (v == null) continue;
            if (MASK.equals(v)) continue;
            out.put(k, v);
        }
        return out.isEmpty() ? Map.of() : out;
    }

    private static Map<String, String> maskHeaders(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String k = toNonBlank(e.getKey());
            if (k == null) continue;
            out.put(k, MASK);
        }
        return out.isEmpty() ? Map.of() : out;
    }

    private static Map<String, String> mergeHeaders(Map<String, String> oldHeaders, Map<String, String> payloadHeaders) {
        Map<String, String> old = oldHeaders == null ? Map.of() : oldHeaders;
        Map<String, String> payload = payloadHeaders == null ? Map.of() : payloadHeaders;
        if (payload.isEmpty()) return Map.of();

        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : payload.entrySet()) {
            String k = toNonBlank(e.getKey());
            if (k == null) continue;
            String vNorm = toNonBlank(e.getValue());
            if (vNorm == null) continue;
            if (MASK.equals(vNorm)) {
                String oldVal = toNonBlank(old.get(k));
                if (oldVal != null) out.put(k, oldVal);
                continue;
            }
            out.put(k, vNorm);
        }
        return out;
    }
}
