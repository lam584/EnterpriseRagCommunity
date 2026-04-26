package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelsDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiUpstreamModelsPreviewRequestDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.exception.UpstreamRequestException;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiProviderModelsAdminService {
    private static final String ENV_DEFAULT = "default";

    private final LlmModelRepository llmModelRepository;
    private final LlmProviderRepository llmProviderRepository;
    private final AiProvidersConfigService aiProvidersConfigService;
    private final ObjectMapper objectMapper;
    private final LlmRoutingService llmRoutingService;

    @Transactional(readOnly = true)
    public AiProviderModelsDTO listProviderModels(String providerId) {
        String pid = toNonBlank(providerId);
        if (pid == null) throw new IllegalArgumentException("providerId 不能为空");

        List<LlmModelEntity> rows = llmModelRepository.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(ENV_DEFAULT, pid);
        List<AiProviderModelDTO> out = new ArrayList<>();
        for (LlmModelEntity e : rows) {
            if (e == null) continue;
            String purpose = toPurposeOrNull(e.getPurpose());
            String name = toNonBlank(e.getModelName());
            if (purpose == null || name == null) continue;
            AiProviderModelDTO m = new AiProviderModelDTO();
            m.setPurpose(purpose);
            m.setModelName(name);
            m.setEnabled(e.getEnabled() == null || e.getEnabled());
            out.add(m);
        }

        out.sort(Comparator
            .comparing(AiProviderModelsAdminService::purposeSortKey)
            .thenComparing(AiProviderModelsAdminService::modelNameSortKey));

        AiProviderModelsDTO dto = new AiProviderModelsDTO();
        dto.setProviderId(pid);
        dto.setModels(out);
        return dto;
    }

    @Transactional
    public AiProviderModelsDTO addProviderModel(String providerId, String purpose, String modelName, Long actorUserId) {
        ModelIdentity identity = requireModelIdentity(providerId, purpose, modelName);
        String pid = identity.providerId();
        String purp = identity.purpose();
        String name = identity.modelName();

        boolean providerExists = llmProviderRepository.findByEnvAndProviderId(ENV_DEFAULT, pid).isPresent();
        if (!providerExists) {
            throw new IllegalArgumentException("模型提供商不存在（providerId=" + pid + "），请先保存“模型提供商”配置后再添加模型");
        }

        Optional<LlmModelEntity> existing = llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(ENV_DEFAULT, pid, purp, name);
        if (existing.isPresent()) {
            LlmModelEntity e = existing.get();
            if (Boolean.FALSE.equals(e.getEnabled())) {
                e.setEnabled(true);
                e.setUpdatedAt(LocalDateTime.now());
                e.setUpdatedBy(actorUserId);
                llmModelRepository.save(e);
            }
            return listProviderModels(pid);
        }

        LocalDateTime now = LocalDateTime.now();
        LlmModelEntity e = new LlmModelEntity();
        e.setEnv(ENV_DEFAULT);
        e.setProviderId(pid);
        e.setPurpose(purp);
        e.setModelName(name);
        e.setEnabled(true);
        e.setIsDefault(false);
        e.setWeight(0);
        e.setPriority(0);

        // Calculate next sort index
        int maxSortIndex = -1;
        List<LlmModelEntity> allModels = llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(ENV_DEFAULT);
        for (LlmModelEntity m : allModels) {
            if (m != null && purp.equals(m.getPurpose())) {
                if (m.getSortIndex() != null && m.getSortIndex() > maxSortIndex) {
                    maxSortIndex = m.getSortIndex();
                }
            }
        }
        e.setSortIndex(maxSortIndex + 1);

        e.setQps(null);
        e.setPriceConfigId(null);
        e.setMetadata(null);
        e.setCreatedAt(now);
        e.setCreatedBy(actorUserId);
        e.setUpdatedAt(now);
        e.setUpdatedBy(actorUserId);
        llmModelRepository.save(e);

        llmRoutingService.resetRuntimeState();

        return listProviderModels(pid);
    }

    @Transactional
    public AiProviderModelsDTO deleteProviderModel(String providerId, String purpose, String modelName) {
        ModelIdentity identity = requireModelIdentity(providerId, purpose, modelName);
        String pid = identity.providerId();
        String purp = identity.purpose();
        String name = identity.modelName();

        llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(ENV_DEFAULT, pid, purp, name).ifPresent(llmModelRepository::delete);
        llmRoutingService.resetRuntimeState();
        return listProviderModels(pid);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchUpstreamModels(String providerId) {
        String pid = toNonBlank(providerId);
        if (pid == null) throw new IllegalArgumentException("providerId 不能为空");

        AiProvidersConfigService.ResolvedProvider p = aiProvidersConfigService.resolveProvider(pid);
        String endpoint = buildEndpoint(p.baseUrl(), "/models");

        Integer connectTimeoutObj = p.connectTimeoutMs();
        int connectTimeout = (connectTimeoutObj == null || connectTimeoutObj <= 0) ? 10_000 : connectTimeoutObj;
        Integer readTimeoutObj = p.readTimeoutMs();
        int readTimeout = (readTimeoutObj == null || readTimeoutObj <= 0) ? 60_000 : readTimeoutObj;
        List<String> models = requestUpstreamModels(endpoint, p.apiKey(), p.extraHeaders(), connectTimeout, readTimeout);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("providerId", pid);
        out.put("models", models);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewUpstreamModels(AiUpstreamModelsPreviewRequestDTO req) {
        if (req == null) throw new IllegalArgumentException("payload 不能为空");
        String baseUrl = toNonBlank(req.getBaseUrl());
        if (baseUrl == null) throw new IllegalArgumentException("baseUrl 不能为空");
        String endpoint = buildEndpoint(baseUrl, "/models");
        int connectTimeoutMs = req.getConnectTimeoutMs() == null ? 10_000 : Math.max(1, req.getConnectTimeoutMs());
        int readTimeoutMs = req.getReadTimeoutMs() == null ? 60_000 : Math.max(1, req.getReadTimeoutMs());

        List<String> models = requestUpstreamModels(endpoint, req.getApiKey(), req.getExtraHeaders(), connectTimeoutMs, readTimeoutMs);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("providerId", toNonBlank(req.getProviderId()));
        out.put("models", models);
        return out;
    }

    private static void validateHttpEndpoint(String endpoint) {
        if (endpoint == null) throw new IllegalArgumentException("endpoint 不能为空");
        String trimmed = endpoint.trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException("endpoint 不能为空");
        try {
            URL url = java.net.URI.create(trimmed).toURL();
            validateHttpUrl(url);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("endpoint 格式不合法", e);
        }
    }

    private static String purposeSortKey(AiProviderModelDTO model) {
        return model == null || model.getPurpose() == null ? "" : model.getPurpose();
    }

    private static String modelNameSortKey(AiProviderModelDTO model) {
        return model == null || model.getModelName() == null ? "" : model.getModelName();
    }

    private static String buildConnectErrorMessage(String endpoint, ConnectException e) {
        String reason = e == null || e.getMessage() == null || e.getMessage().isBlank() ? "Connection refused" : e.getMessage();
        boolean local = false;
        try {
            URL url = java.net.URI.create(endpoint).toURL();
            String host = url.getHost();
            if (host != null) {
                String h = host.trim().toLowerCase(Locale.ROOT);
                local = h.equals("localhost") || h.equals("127.0.0.1") || h.equals("0.0.0.0");
            }
        } catch (Exception ignore) {
        }
        if (local) {
            return "获取 /v1/models 失败: 无法连接到 " + endpoint + "（" + reason + "）。baseUrl 指向 localhost/127.0.0.1 时，必须确保后端进程本机能访问该地址；若上游跑在你的电脑而后端跑在其他机器/容器，请改成后端可达的地址（如宿主机 IP 或 host.docker.internal）。";
        }
        return "获取 /v1/models 失败: 无法连接到 " + endpoint + "（" + reason + "）。请确认上游服务已启动、端口/协议正确且后端网络可达。";
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String safeSnippet(String s) {
        if (s == null) return "";
        if (s.length() <= 2000) return s;
        return s.substring(0, 2000) + "...";
    }

    private static void applyHeaders(HttpURLConnection conn, String apiKey, Map<String, String> extraHeaders) {
        boolean hasAuth = false;
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k == null || k.isBlank()) continue;
                if (v == null) continue;
                conn.setRequestProperty(k, v);
                if ("authorization".equals(k.trim().toLowerCase(Locale.ROOT))) {
                    hasAuth = true;
                }
            }
        }

        if (!hasAuth && apiKey != null && !apiKey.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
    }

    private static String buildEndpoint(String baseUrl, String path) {
        String endpoint = baseUrl == null ? "" : baseUrl.trim();
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return endpoint + path;
    }

    private static void validateHttpUrl(URL url) {
        if (url == null) throw new IllegalArgumentException("URL 不能为空");
        String protocol = url.getProtocol();
        if (protocol == null) throw new IllegalArgumentException("URL 协议不合法");
        String p = protocol.trim().toLowerCase(Locale.ROOT);
        if (!p.equals("http") && !p.equals("https")) {
            throw new IllegalArgumentException("仅支持 http/https URL");
        }
        String host = url.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("URL host 不能为空");
    }

    private List<String> requestUpstreamModels(
            String endpoint,
            String apiKey,
            Map<String, String> extraHeaders,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
        try {
            validateHttpEndpoint(endpoint);
            URL url = java.net.URI.create(endpoint).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("Accept", "application/json");
            applyHeaders(conn, apiKey, extraHeaders);

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                throw new UpstreamRequestException(HttpStatus.BAD_GATEWAY, "Upstream 返回 HTTP " + code + " 且无响应体");
            }

            byte[] raw = is.readAllBytes();
            String body = new String(raw, StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                String snippet = safeSnippet(body);
                throw new UpstreamRequestException(HttpStatus.BAD_GATEWAY, "Upstream 返回 HTTP " + code + ": " + snippet);
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root == null ? null : root.get("data");
            List<String> models = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    if (item == null) continue;
                    String id = item.hasNonNull("id") ? item.get("id").asText(null) : null;
                    if (id == null || id.isBlank()) continue;
                    models.add(id.trim());
                }
            }
            models.sort(String::compareTo);
            return models;
        } catch (UpstreamRequestException e) {
            throw e;
        } catch (ConnectException e) {
            throw new UpstreamRequestException(HttpStatus.BAD_GATEWAY, buildConnectErrorMessage(endpoint, e), e);
        } catch (Exception e) {
            Throwable root = rootCause(e);
            if (root instanceof ConnectException ce) {
                throw new UpstreamRequestException(HttpStatus.BAD_GATEWAY, buildConnectErrorMessage(endpoint, ce), ce);
            }
            String msg = root == null || root.getMessage() == null || root.getMessage().isBlank() ? "未知错误" : root.getMessage();
            throw new UpstreamRequestException(HttpStatus.BAD_GATEWAY, "获取 /v1/models 失败: " + msg + "（endpoint: " + endpoint + "）", e);
        }
    }

    private static String toNonBlank(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isBlank() ? null : s;
    }

    private static String toPurposeOrNull(String v) {
        String s = toNonBlank(v);
        if (s == null) return null;
        String x = s.toUpperCase(Locale.ROOT);
        try {
            LlmQueueTaskType tt = LlmQueueTaskType.valueOf(x);
            if (tt == LlmQueueTaskType.UNKNOWN) return null;
            return tt.name();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static ModelIdentity requireModelIdentity(String providerId, String purpose, String modelName) {
        String pid = toNonBlank(providerId);
        if (pid == null) throw new IllegalArgumentException("providerId 不能为空");
        String purp = toPurposeOrNull(purpose);
        if (purp == null) throw new IllegalArgumentException("purpose 不合法");
        String name = toNonBlank(modelName);
        if (name == null) throw new IllegalArgumentException("modelName 不能为空");
        return new ModelIdentity(pid, purp, name);
    }

    private record ModelIdentity(String providerId, String purpose, String modelName) {
    }
}
