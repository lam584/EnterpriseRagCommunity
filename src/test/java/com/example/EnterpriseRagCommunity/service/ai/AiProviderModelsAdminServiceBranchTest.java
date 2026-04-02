package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelsDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiUpstreamModelsPreviewRequestDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderEntity;
import com.example.EnterpriseRagCommunity.exception.UpstreamRequestException;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.lang.reflect.Method;
import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiProviderModelsAdminServiceBranchTest {

    @Test
    void addProviderModel_shouldThrow_whenProviderIdBlank() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.addProviderModel("  ", "POST_EMBEDDING", "m1", 1L));

        assertTrue(ex.getMessage().contains("providerId 不能为空"));
    }

    @Test
    void addProviderModel_shouldThrow_whenPurposeInvalid() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.addProviderModel("p1", "UNKNOWN", "m1", 1L));

        assertTrue(ex.getMessage().contains("purpose 不合法"));
    }

    @Test
    void addProviderModel_shouldThrow_whenModelNameBlank() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.addProviderModel("p1", "POST_EMBEDDING", "   ", 1L));

        assertTrue(ex.getMessage().contains("modelName 不能为空"));
    }

    @Test
    void listProviderModels_shouldThrow_whenProviderIdBlank() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.listProviderModels("   "));

        assertTrue(ex.getMessage().contains("providerId 不能为空"));
    }

    @Test
    void listProviderModels_shouldFilterInvalidRowsAndSort() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, providersConfig, routingService);

        LlmModelEntity invalidPurpose = model("p1", "UNKNOWN", "m0", true, 0);
        LlmModelEntity invalidName = model("p1", "TEXT_CHAT", "   ", true, 0);
        LlmModelEntity rowA = model("p1", "TEXT_CHAT", "z-model", false, 0);
        LlmModelEntity rowB = model("p1", "POST_EMBEDDING", "a-model", null, 0);

        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
            .thenReturn(Arrays.asList(null, invalidPurpose, invalidName, rowA, rowB));

        AiProviderModelsDTO out = svc.listProviderModels(" p1 ");

        assertEquals("p1", out.getProviderId());
        assertNotNull(out.getModels());
        assertEquals(2, out.getModels().size());
        assertEquals("POST_EMBEDDING", out.getModels().get(0).getPurpose());
        assertEquals("a-model", out.getModels().get(0).getModelName());
        assertEquals(true, out.getModels().get(0).getEnabled());
        assertEquals("TEXT_CHAT", out.getModels().get(1).getPurpose());
        assertEquals("z-model", out.getModels().get(1).getModelName());
        assertEquals(false, out.getModels().get(1).getEnabled());
    }

    @Test
    void addProviderModel_shouldThrow_whenProviderMissing() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        when(providerRepo.findByEnvAndProviderId(eq("default"), eq("p1"))).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.addProviderModel("p1", "POST_EMBEDDING", "m1", 1L));

        assertTrue(ex.getMessage().contains("模型提供商不存在"));
    }

    @Test
    void addProviderModel_shouldReEnableExistingDisabledModel() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, mock(AiProvidersConfigService.class), routingService);

        LlmModelEntity existing = model("p1", "POST_EMBEDDING", "m1", false, 3);

        when(providerRepo.findByEnvAndProviderId(eq("default"), eq("p1"))).thenReturn(Optional.of(new LlmProviderEntity()));
        when(modelRepo.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("POST_EMBEDDING"), eq("m1")))
                .thenReturn(Optional.of(existing));
        when(modelRepo.save(any(LlmModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
                .thenReturn(List.of(existing));

        AiProviderModelsDTO out = svc.addProviderModel("p1", "POST_EMBEDDING", "m1", 7L);

        assertEquals("p1", out.getProviderId());
        assertEquals(true, existing.getEnabled());
        assertEquals(7L, existing.getUpdatedBy());
        verify(modelRepo, times(1)).save(existing);
        verify(routingService, never()).resetRuntimeState();
    }

    @Test
    void addProviderModel_shouldNotSave_whenExistingEnabledTrue() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, mock(AiProvidersConfigService.class), routingService);

        LlmModelEntity existing = model("p1", "POST_EMBEDDING", "m1", true, 2);

        when(providerRepo.findByEnvAndProviderId(eq("default"), eq("p1"))).thenReturn(Optional.of(new LlmProviderEntity()));
        when(modelRepo.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("POST_EMBEDDING"), eq("m1")))
                .thenReturn(Optional.of(existing));
        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
                .thenReturn(List.of(existing));

        svc.addProviderModel("p1", "POST_EMBEDDING", "m1", 7L);

        verify(modelRepo, never()).save(any(LlmModelEntity.class));
        verify(routingService, never()).resetRuntimeState();
    }

    @Test
    void addProviderModel_shouldAssignSortIndexFromSamePurpose() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, mock(AiProvidersConfigService.class), routingService);

        LlmModelEntity m1 = model("p1", "POST_EMBEDDING", "a", true, 1);
        LlmModelEntity m2 = model("p1", "POST_EMBEDDING", "b", true, 5);
        LlmModelEntity m3 = model("p2", "TEXT_CHAT", "x", true, 99);
        LlmModelEntity m4 = model("p1", "POST_EMBEDDING", "c", true, null);

        when(providerRepo.findByEnvAndProviderId(eq("default"), eq("p1"))).thenReturn(Optional.of(new LlmProviderEntity()));
        when(modelRepo.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("POST_EMBEDDING"), eq("m-new")))
                .thenReturn(Optional.empty());
        when(modelRepo.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default")))
            .thenReturn(Arrays.asList(null, m1, m2, m3, m4));
        when(modelRepo.save(any(LlmModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
                .thenReturn(List.of(m1, m2));

        svc.addProviderModel("p1", "POST_EMBEDDING", "m-new", 10L);

        ArgumentCaptor<LlmModelEntity> captor = ArgumentCaptor.forClass(LlmModelEntity.class);
        verify(modelRepo).save(captor.capture());
        assertEquals(6, captor.getValue().getSortIndex());
        verify(routingService).resetRuntimeState();
    }

    @Test
    void addProviderModel_shouldKeepMaxSortIndex_whenSamePurposeLowerSortIndexExists() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, mock(AiProvidersConfigService.class), routingService);

        LlmModelEntity max = model("p1", "POST_EMBEDDING", "m-max", true, 9);
        LlmModelEntity lower = model("p1", "POST_EMBEDDING", "m-lower", true, 3);

        when(providerRepo.findByEnvAndProviderId(eq("default"), eq("p1"))).thenReturn(Optional.of(new LlmProviderEntity()));
        when(modelRepo.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("POST_EMBEDDING"), eq("m-new")))
                .thenReturn(Optional.empty());
        when(modelRepo.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default")))
                .thenReturn(List.of(max, lower));
        when(modelRepo.save(any(LlmModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
                .thenReturn(List.of(max, lower));

        svc.addProviderModel("p1", "POST_EMBEDDING", "m-new", 10L);

        ArgumentCaptor<LlmModelEntity> captor = ArgumentCaptor.forClass(LlmModelEntity.class);
        verify(modelRepo).save(captor.capture());
        assertEquals(10, captor.getValue().getSortIndex());
    }

    @Test
    void deleteProviderModel_shouldDeleteAndResetWhenPresent() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, mock(AiProvidersConfigService.class), routingService);

        LlmModelEntity existing = model("p1", "POST_EMBEDDING", "m1", true, 1);
        when(modelRepo.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("POST_EMBEDDING"), eq("m1")))
                .thenReturn(Optional.of(existing));
        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
                .thenReturn(List.of());

        svc.deleteProviderModel("p1", "POST_EMBEDDING", "m1");

        verify(modelRepo).delete(existing);
        verify(routingService).resetRuntimeState();
    }

    @Test
    void deleteProviderModel_shouldThrow_whenProviderIdBlank() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.deleteProviderModel("  ", "POST_EMBEDDING", "m1"));

        assertTrue(ex.getMessage().contains("providerId 不能为空"));
    }

    @Test
    void deleteProviderModel_shouldThrow_whenPurposeInvalid() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.deleteProviderModel("p1", "UNKNOWN", "m1"));

        assertTrue(ex.getMessage().contains("purpose 不合法"));
    }

    @Test
    void deleteProviderModel_shouldThrow_whenModelNameBlank() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.deleteProviderModel("p1", "POST_EMBEDDING", "   "));

        assertTrue(ex.getMessage().contains("modelName 不能为空"));
    }

    @Test
    void deleteProviderModel_shouldResetWithoutDelete_whenModelNotFound() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, mock(AiProvidersConfigService.class), routingService);

        when(modelRepo.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("POST_EMBEDDING"), eq("m1")))
                .thenReturn(Optional.empty());
        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
                .thenReturn(List.of());

        svc.deleteProviderModel("p1", "POST_EMBEDDING", "m1");

        verify(modelRepo, never()).delete(any(LlmModelEntity.class));
        verify(routingService).resetRuntimeState();
    }

    @Test
    void fetchUpstreamModels_shouldThrow_whenProviderIdBlank() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.fetchUpstreamModels("   "));

        assertTrue(ex.getMessage().contains("providerId 不能为空"));
    }

    @Test
    void previewUpstreamModels_shouldThrow_whenPayloadNull() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.previewUpstreamModels(null));

        assertTrue(ex.getMessage().contains("payload 不能为空"));
    }

    @Test
    void previewUpstreamModels_shouldThrow_whenBaseUrlBlank() {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
        req.setBaseUrl("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.previewUpstreamModels(req));

        assertTrue(ex.getMessage().contains("baseUrl 不能为空"));
    }

    @Test
    void previewUpstreamModels_shouldClampTimeoutAndSortModels() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, providersConfig, routingService);

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":[{\"id\":\"b\"},{\"id\":\" a \"},{\"id\":\"\"},{}]}".getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setProviderId(" p1 ");
            req.setBaseUrl("http://api.example.com/v1");
            req.setConnectTimeoutMs(0);
            req.setReadTimeoutMs(0);

            Map<String, Object> out = svc.previewUpstreamModels(req);

            assertEquals("p1", out.get("providerId"));
            assertEquals(List.of("a", "b"), out.get("models"));
            verify(conn).setConnectTimeout(1);
            verify(conn).setReadTimeout(1);
            verify(conn).setRequestMethod("GET");
            verify(conn).setRequestProperty("Accept", "application/json");
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void previewUpstreamModels_shouldUseDefaultTimeout_whenTimeoutNull() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":[{\"id\":\"m\"}]}".getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            Map<String, Object> out = svc.previewUpstreamModels(req);

            assertEquals(List.of("m"), out.get("models"));
            verify(conn).setConnectTimeout(10_000);
            verify(conn).setReadTimeout(60_000);
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void fetchUpstreamModels_shouldUseDefaultTimeout_whenProviderTimeoutInvalid() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, providersConfig, routingService);

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":[{\"id\":\"m2\"},{\"id\":\"m1\"}]}".getBytes(StandardCharsets.UTF_8)));

        when(providersConfig.resolveProvider("p1")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://gateway.example.com/v1",
                "ak",
                "chat",
                "embed",
                Map.of(),
                Map.of("authorization", "Token x"),
                0,
                -1
        ));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            Map<String, Object> out = svc.fetchUpstreamModels("p1");

            assertEquals(List.of("m1", "m2"), out.get("models"));
            verify(conn).setConnectTimeout(10_000);
            verify(conn).setReadTimeout(60_000);
            verify(conn).setRequestProperty("authorization", "Token x");
            verify(conn, never()).setRequestProperty("Authorization", "Bearer ak");
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void fetchUpstreamModels_shouldAddBearerAuthorization_whenNoAuthorizationHeaderProvided() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, providersConfig, routingService);

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":[{\"id\":\"m1\"}]}".getBytes(StandardCharsets.UTF_8)));

        when(providersConfig.resolveProvider("p1")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://gateway.example.com/v1",
                "ak",
                "chat",
                "embed",
                Map.of(),
                Map.of("X-Trace", "t1"),
                1234,
                5678
        ));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            Map<String, Object> out = svc.fetchUpstreamModels("p1");

            assertEquals(List.of("m1"), out.get("models"));
            verify(conn).setConnectTimeout(1234);
            verify(conn).setReadTimeout(5678);
            verify(conn).setRequestProperty("X-Trace", "t1");
            verify(conn).setRequestProperty("Authorization", "Bearer ak");
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void fetchUpstreamModels_shouldIgnoreBlankHeaderAndStillAddBearer() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, providersConfig, routingService);

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":[{\"id\":\"m1\"}]}".getBytes(StandardCharsets.UTF_8)));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(" ", "x");
        headers.put("X-K", null);

        when(providersConfig.resolveProvider("p1")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://gateway.example.com/v1",
                "ak",
                "chat",
                "embed",
                Map.of(),
                headers,
                null,
                null
        ));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            Map<String, Object> out = svc.fetchUpstreamModels("p1");

            assertEquals(List.of("m1"), out.get("models"));
            verify(conn, never()).setRequestProperty(eq(" "), any(String.class));
            verify(conn, never()).setRequestProperty(eq("X-K"), any(String.class));
            verify(conn).setRequestProperty("Authorization", "Bearer ak");
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void previewUpstreamModels_shouldReturnEmpty_whenDataMissingOrNotArray() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":{\"id\":\"x\"}}".getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            Map<String, Object> out = svc.previewUpstreamModels(req);

            assertNotNull(out.get("models"));
            assertEquals(List.of(), out.get("models"));
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void previewUpstreamModels_shouldNotAddBearer_whenApiKeyBlankAndNoHeaders() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":[]}".getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");
            req.setApiKey("   ");

            Map<String, Object> out = svc.previewUpstreamModels(req);

            assertEquals(List.of(), out.get("models"));
            verify(conn, never()).setRequestProperty(eq("Authorization"), any(String.class));
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    private static String host(String endpoint) {
        try {
            String h = URI.create(endpoint).getHost();
            return (h == null || h.isBlank()) ? "example.com" : h;
        } catch (Exception ignore) {
            return "example.com";
        }
    }

    @Test
    void previewUpstreamModels_shouldThrow_whenProtocolNotHttpOrHttps() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
        req.setBaseUrl("ftp://api.example.com/v1");

        UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> svc.previewUpstreamModels(req));
        assertTrue(ex.getMessage().contains("仅支持 http/https"));
    }

    @Test
    void previewUpstreamModels_shouldThrow_whenHostMissing() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));

        AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
        req.setBaseUrl("http:///v1");

        UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> svc.previewUpstreamModels(req));
        assertTrue(ex.getMessage().contains("获取 /v1/models 失败"));
    }

    @Test
    void previewUpstreamModels_shouldUseSingleSlashInEndpoint_whenBaseUrlEndsWithSlash() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        AiProviderModelsAdminService svc = newService(modelRepo, providerRepo, providersConfig, routingService);

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":[]}".getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1/");

            Map<String, Object> out = svc.previewUpstreamModels(req);

            assertEquals(List.of(), out.get("models"));
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void previewUpstreamModels_shouldHandleWrappedConnectException() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenThrow(new RuntimeException("outer", new ConnectException("conn reset")));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> svc.previewUpstreamModels(req));
            assertTrue(ex.getMessage().contains("无法连接到"));
            assertTrue(ex.getMessage().contains("请确认上游服务已启动"));
        }
    }

    @Test
    void previewUpstreamModels_shouldWrapGenericErrorMessage() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenThrow(new IllegalStateException("boom"));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> svc.previewUpstreamModels(req));
            assertTrue(ex.getMessage().contains("获取 /v1/models 失败: boom"));
            assertTrue(ex.getMessage().contains("endpoint: http://api.example.com/v1/models"));
        }
    }

    @Test
    void previewUpstreamModels_shouldUseUnknownError_whenRootMessageBlank() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenThrow(new RuntimeException(""));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> svc.previewUpstreamModels(req));
            assertTrue(ex.getMessage().contains("获取 /v1/models 失败: 未知错误"));
        }
    }

    @Test
    void previewUpstreamModels_shouldThrowBadGateway_whenResponseCodeBelow200() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(199);
        when(conn.getErrorStream()).thenReturn(new ByteArrayInputStream("bad".getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> svc.previewUpstreamModels(req));
            assertTrue(ex.getMessage().contains("HTTP 199"));
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void previewUpstreamModels_shouldReturnEmpty_whenObjectMapperReturnsNullRoot() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        LlmRoutingService routingService = mock(LlmRoutingService.class);
        ObjectMapper mapper = new ObjectMapper() {
            @Override
            public JsonNode readTree(String content) {
                return null;
            }
        };
        AiProviderModelsAdminService svc = new AiProviderModelsAdminService(modelRepo, providerRepo, providersConfig, mapper, routingService);

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            Map<String, Object> out = svc.previewUpstreamModels(req);

            assertEquals(List.of(), out.get("models"));
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void previewUpstreamModels_shouldSkipNullArrayItem() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("{\"data\":[null,{\"id\":\"z\"}]}".getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            Map<String, Object> out = svc.previewUpstreamModels(req);

            assertEquals(List.of("z"), out.get("models"));
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void previewUpstreamModels_shouldUseUnknownError_whenRootMessageNull() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenThrow(new RuntimeException((String) null));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://api.example.com/v1");

            UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> svc.previewUpstreamModels(req));
            assertTrue(ex.getMessage().contains("获取 /v1/models 失败: 未知错误"));
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void privateHelpers_shouldCoverComparatorHeadersAndEndpointBranches() throws Exception {
        Method comparatorPurpose = AiProviderModelsAdminService.class.getDeclaredMethod("lambda$listProviderModels$0", com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelDTO.class);
        comparatorPurpose.setAccessible(true);
        com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelDTO empty = new com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelDTO();
        assertEquals("", comparatorPurpose.invoke(null, empty));

        Method comparatorModel = AiProviderModelsAdminService.class.getDeclaredMethod("lambda$listProviderModels$1", com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelDTO.class);
        comparatorModel.setAccessible(true);
        assertEquals("", comparatorModel.invoke(null, empty));

        Method applyHeaders = AiProviderModelsAdminService.class.getDeclaredMethod("applyHeaders", HttpURLConnection.class, String.class, Map.class);
        applyHeaders.setAccessible(true);
        HttpURLConnection conn = mock(HttpURLConnection.class);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(null, "v1");
        applyHeaders.invoke(null, conn, "ak", headers);
        verify(conn, never()).setRequestProperty(eq((String) null), any(String.class));
        verify(conn).setRequestProperty("Authorization", "Bearer ak");

        Method buildEndpoint = AiProviderModelsAdminService.class.getDeclaredMethod("buildEndpoint", String.class, String.class);
        buildEndpoint.setAccessible(true);
        assertEquals("/models", buildEndpoint.invoke(null, null, "models"));
        assertEquals("http://api.example.com/v1/models", buildEndpoint.invoke(null, "http://api.example.com/v1", "/models"));
    }

    @Test
    void privateHelpers_shouldCoverUrlAndConnectMessageBranches() throws Exception {
        Method buildConnectErrorMessage = AiProviderModelsAdminService.class.getDeclaredMethod("buildConnectErrorMessage", String.class, ConnectException.class);
        buildConnectErrorMessage.setAccessible(true);
        String m1 = (String) buildConnectErrorMessage.invoke(null, "http://localhost:8080/v1/models", null);
        String m2 = (String) buildConnectErrorMessage.invoke(null, "http://0.0.0.0:8080/v1/models", new ConnectException("c2"));
        String m3 = (String) buildConnectErrorMessage.invoke(null, "http:///v1/models", new ConnectException((String) null));
        assertTrue(m1.contains("localhost/127.0.0.1"));
        assertTrue(m2.contains("localhost/127.0.0.1"));
        assertTrue(m3.contains("请确认上游服务已启动"));

        Method validateHttpUrl = AiProviderModelsAdminService.class.getDeclaredMethod("validateHttpUrl", URL.class);
        validateHttpUrl.setAccessible(true);

        URL httpsUrl = mock(URL.class);
        when(httpsUrl.getProtocol()).thenReturn("https");
        when(httpsUrl.getHost()).thenReturn("api.example.com");
        validateHttpUrl.invoke(null, httpsUrl);

        URL blankHost = mock(URL.class);
        when(blankHost.getProtocol()).thenReturn("http");
        when(blankHost.getHost()).thenReturn("   ");
        Exception ex = assertThrows(Exception.class, () -> validateHttpUrl.invoke(null, blankHost));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("URL host 不能为空"));
    }

    private static AiProviderModelsAdminService newService(
            LlmModelRepository modelRepo,
            LlmProviderRepository providerRepo,
            AiProvidersConfigService providersConfig,
            LlmRoutingService routingService
    ) {
        return new AiProviderModelsAdminService(
                modelRepo,
                providerRepo,
                providersConfig,
                new ObjectMapper(),
                routingService
        );
    }

    private static LlmModelEntity model(String providerId, String purpose, String modelName, Boolean enabled, Integer sortIndex) {
        LlmModelEntity e = new LlmModelEntity();
        e.setEnv("default");
        e.setProviderId(providerId);
        e.setPurpose(purpose);
        e.setModelName(modelName);
        e.setEnabled(enabled);
        e.setSortIndex(sortIndex);
        return e;
    }

    private static String protocol(String endpoint) {
        String lower = endpoint == null ? "" : endpoint.toLowerCase();
        if (lower.startsWith("https://")) return "https";
        if (lower.startsWith("http://")) return "http";
        if (lower.startsWith("ftp://")) return "ftp";
        return "http";
    }

    @Test
    void previewUpstreamModels_shouldShowLocalhostHint_whenConnectException() throws Exception {
        AiProviderModelsAdminService svc = newService(mock(LlmModelRepository.class), mock(LlmProviderRepository.class), mock(AiProvidersConfigService.class), mock(LlmRoutingService.class));
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenThrow(new ConnectException("Connection refused"));

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
            AiUpstreamModelsPreviewRequestDTO req = new AiUpstreamModelsPreviewRequestDTO();
            req.setBaseUrl("http://127.0.0.1:8080/v1");

            UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> svc.previewUpstreamModels(req));
            assertTrue(ex.getMessage().contains("无法连接到"));
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }
}
