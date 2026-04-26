package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.config.AdminSetupManager;
import com.example.EnterpriseRagCommunity.config.DynamicConfigurationLoader;
import com.example.EnterpriseRagCommunity.config.DynamicElasticsearchConfig;
import com.example.EnterpriseRagCommunity.config.ElasticsearchIndexStartupInitializer;
import com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessLogEsIndexProvisioningService;
import com.example.EnterpriseRagCommunity.service.access.AccessLogKafkaLifecycleManager;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.init.InitialAdminIndexBootstrapService;
import com.example.EnterpriseRagCommunity.utils.AesGcmUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SetupController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "APP_MASTER_KEY=TEST_MASTER_KEY"
})
class SetupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SetupController setupController;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdministratorService administratorService;

    @MockitoBean
    private SystemConfigurationService systemConfigurationService;

    @MockitoBean
    private DynamicConfigurationLoader dynamicConfigurationLoader;

    @MockitoBean
    private DynamicElasticsearchConfig dynamicElasticsearchConfig;

    @MockitoBean
    private ElasticsearchIndexStartupInitializer elasticsearchIndexStartupInitializer;

    @MockitoBean
    private AuthController authController;

    @MockitoBean
    private InitialAdminIndexBootstrapService initialAdminIndexBootstrapService;

    @MockitoBean
    private AesGcmUtils aesGcmUtils;

    @MockitoBean
    private SetupController.RestClientFactory restClientFactory;

    @MockitoBean
    private AdminSetupManager adminSetupManager;

    @MockitoBean
    private AccessLogKafkaLifecycleManager accessLogKafkaLifecycleManager;

    @MockitoBean
    private AccessLogEsIndexProvisioningService accessLogEsIndexProvisioningService;

    private final String originalUserDir = System.getProperty("user.dir");

    @AfterEach
    void restoreUserDir() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @BeforeEach
    void setupDefaults() {
        when(adminSetupManager.isSetupRequired()).thenReturn(true);
    }

    @Test
    void status_shouldReturnInitializedFalse_whenNoAdmins() throws Exception {
        when(administratorService.countAdministrators()).thenReturn(0L);

        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isInitialized").value(false));
    }

    @Test
    void status_shouldReturnInitializedTrue_whenHasAdmins() throws Exception {
        when(administratorService.countAdministrators()).thenReturn(1L);

        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isInitialized").value(true));
    }

    @Test
    void checkEnv_shouldReturnExistsFalse_whenNoEnvFile(@TempDir Path tmp) throws Exception {
        System.setProperty("user.dir", tmp.toString());

        mockMvc.perform(get("/api/setup/check-env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    void checkEnv_shouldReturnExistsTrue_whenEnvFilePresent(@TempDir Path tmp) throws Exception {
        System.setProperty("user.dir", tmp.toString());
        Files.writeString(tmp.resolve(".env"), "X=1", StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/setup/check-env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    void encrypt_shouldReturn400_whenValueBlank() throws Exception {
        mockMvc.perform(post("/api/setup/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Value is required"));
    }

    @Test
    void encrypt_shouldReturn400_whenValueMissing() throws Exception {
        mockMvc.perform(post("/api/setup/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Value is required"));
    }

    @Test
    void encrypt_shouldReturn200_whenSuccess() throws Exception {
        when(aesGcmUtils.encrypt("v", "TEST_MASTER_KEY")).thenReturn("enc");

        mockMvc.perform(post("/api/setup/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"v\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted").value("enc"));
    }

    @Test
    void encrypt_shouldReturn500_whenEncryptThrows() throws Exception {
        when(aesGcmUtils.encrypt(eq("v"), anyString())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/setup/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"v\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Encryption failed"));
    }

    @Test
    void testEs_shouldReturn400_whenUrisMissing() throws Exception {
        mockMvc.perform(post("/api/setup/test-es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elasticsearch URI is required"));
    }

    @Test
    void testEs_shouldReturn200_whenStatus200() throws Exception {
        RestClient client = org.mockito.Mockito.mock(RestClient.class);
        Response resp = org.mockito.Mockito.mock(Response.class);
        org.apache.http.StatusLine statusLine = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(resp.getStatusLine()).thenReturn(statusLine);
        when(client.performRequest(any(org.elasticsearch.client.Request.class))).thenReturn(resp);
        when(restClientFactory.create(anyString(), nullable(String.class))).thenReturn(client);

        mockMvc.perform(post("/api/setup/test-es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spring.elasticsearch.uris\":\"http://x\",\"APP_ES_API_KEY\":\"k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Connected successfully"));
    }

    @Test
    void testEs_shouldReturn400_whenStatus401() throws Exception {
        RestClient client = org.mockito.Mockito.mock(RestClient.class);
        Response resp = org.mockito.Mockito.mock(Response.class);
        org.apache.http.StatusLine statusLine = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(401);
        when(resp.getStatusLine()).thenReturn(statusLine);
        when(client.performRequest(any(org.elasticsearch.client.Request.class))).thenReturn(resp);
        when(restClientFactory.create(anyString(), nullable(String.class))).thenReturn(client);

        mockMvc.perform(post("/api/setup/test-es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spring.elasticsearch.uris\":\"http://x\",\"APP_ES_API_KEY\":\"k\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication failed (401). Please check your APP_ES_API_KEY."));
    }

    @Test
    void testEs_shouldReturn400_whenOtherStatus() throws Exception {
        RestClient client = org.mockito.Mockito.mock(RestClient.class);
        Response resp = org.mockito.Mockito.mock(Response.class);
        org.apache.http.StatusLine statusLine = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(503);
        when(resp.getStatusLine()).thenReturn(statusLine);
        when(client.performRequest(any(org.elasticsearch.client.Request.class))).thenReturn(resp);
        when(restClientFactory.create(anyString(), nullable(String.class))).thenReturn(client);

        mockMvc.perform(post("/api/setup/test-es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spring.elasticsearch.uris\":\"http://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Connection failed"));
    }

    @Test
    void testEs_shouldReturn400_whenClientThrows() throws Exception {
        RestClient client = org.mockito.Mockito.mock(RestClient.class);
        when(client.performRequest(any(org.elasticsearch.client.Request.class))).thenThrow(new RuntimeException("boom"));
        when(restClientFactory.create(anyString(), nullable(String.class))).thenReturn(client);

        mockMvc.perform(post("/api/setup/test-es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spring.elasticsearch.uris\":\"http://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Connection failed"));
    }

    @Test
    void testEs_shouldReturn400_whenClientFactoryThrows() throws Exception {
        when(restClientFactory.create(anyString(), nullable(String.class))).thenThrow(new RuntimeException("no"));

        mockMvc.perform(post("/api/setup/test-es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spring.elasticsearch.uris\":\"http://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Connection failed"));
    }

    @Test
    void testEs_shouldReturn400_whenClientCloseThrows() throws Exception {
        RestClient client = org.mockito.Mockito.mock(RestClient.class);
        Response resp = org.mockito.Mockito.mock(Response.class);
        org.apache.http.StatusLine statusLine = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(resp.getStatusLine()).thenReturn(statusLine);
        when(client.performRequest(any(org.elasticsearch.client.Request.class))).thenReturn(resp);
        doThrow(new RuntimeException("close")).when(client).close();
        when(restClientFactory.create(anyString(), nullable(String.class))).thenReturn(client);

        mockMvc.perform(post("/api/setup/test-es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spring.elasticsearch.uris\":\"http://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Connection failed"));
    }

    @Test
    void checkIndices_shouldReturn400_whenConfigsOrIndicesMissing() throws Exception {
        mockMvc.perform(post("/api/setup/check-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Configs or indices missing"));
    }

    @Test
    void checkIndices_shouldReturn400_whenIndicesMissingButConfigsPresent() throws Exception {
        mockMvc.perform(post("/api/setup/check-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configs\":{\"spring.elasticsearch.uris\":\"http://x\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Configs or indices missing"));
    }

    @Test
    void checkIndices_shouldReturn400_whenConfigsMissingButIndicesPresent() throws Exception {
        mockMvc.perform(post("/api/setup/check-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"indices\":[\"a\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Configs or indices missing"));
    }

    @Test
    void checkIndices_shouldReturn400_whenUrisMissing() throws Exception {
        mockMvc.perform(post("/api/setup/check-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configs\":{},\"indices\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elasticsearch URI is required"));
    }

    @Test
    void checkIndices_shouldReturn400_whenUrisBlank() throws Exception {
        mockMvc.perform(post("/api/setup/check-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configs\":{\"spring.elasticsearch.uris\":\"   \"},\"indices\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elasticsearch URI is required"));
    }

    @Test
    void checkIndices_shouldMapPerIndexStatuses() throws Exception {
        RestClient client = org.mockito.Mockito.mock(RestClient.class);

        Response r200 = org.mockito.Mockito.mock(Response.class);
        Response r404 = org.mockito.Mockito.mock(Response.class);
        Response r500 = org.mockito.Mockito.mock(Response.class);

        org.apache.http.StatusLine s200 = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        org.apache.http.StatusLine s404 = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        org.apache.http.StatusLine s500 = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);

        when(s200.getStatusCode()).thenReturn(200);
        when(s404.getStatusCode()).thenReturn(404);
        when(s500.getStatusCode()).thenReturn(500);

        when(r200.getStatusLine()).thenReturn(s200);
        when(r404.getStatusLine()).thenReturn(s404);
        when(r500.getStatusLine()).thenReturn(s500);

        when(client.performRequest(any(org.elasticsearch.client.Request.class)))
                .thenReturn(r200, r404, r500)
                .thenThrow(new RuntimeException("boom"));

        when(restClientFactory.create(anyString(), nullable(String.class))).thenReturn(client);

        mockMvc.perform(post("/api/setup/check-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configs": {"spring.elasticsearch.uris":"http://x"},
                                  "indices": ["a","b","c","d"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a").value("已创建"))
                .andExpect(jsonPath("$.b").value("未创建"))
                .andExpect(jsonPath("$.c").value("状态码: 500"))
                .andExpect(jsonPath("$.d").value("检查失败"));
    }

    @Test
    void checkIndices_shouldReturn500_whenClientFactoryThrows() throws Exception {
        when(restClientFactory.create(anyString(), nullable(String.class))).thenThrow(new RuntimeException("no"));

        mockMvc.perform(post("/api/setup/check-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configs": {"spring.elasticsearch.uris":"http://x"},
                                  "indices": ["a"]
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to connect to ES"));
    }

    @Test
    void checkIndices_shouldReturn500_whenClientCloseThrows() throws Exception {
        RestClient client = org.mockito.Mockito.mock(RestClient.class);
        Response r200 = org.mockito.Mockito.mock(Response.class);
        org.apache.http.StatusLine s200 = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        when(s200.getStatusCode()).thenReturn(200);
        when(r200.getStatusLine()).thenReturn(s200);
        when(client.performRequest(any(org.elasticsearch.client.Request.class))).thenReturn(r200);
        doThrow(new RuntimeException("close")).when(client).close();
        when(restClientFactory.create(anyString(), nullable(String.class))).thenReturn(client);

        mockMvc.perform(post("/api/setup/check-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configs": {"spring.elasticsearch.uris":"http://x"},
                                  "indices": ["a"]
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to connect to ES"));
    }

    @Test
    void saveConfig_shouldReturn400_whenConfigsMissing() throws Exception {
        mockMvc.perform(post("/api/setup/save-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Configs are missing"));
    }

    @Test
    void saveConfig_shouldReturn200_whenSuccess_evenIfReinitWarns() throws Exception {
        doThrow(new RuntimeException("warn")).when(elasticsearchIndexStartupInitializer).init();

        mockMvc.perform(post("/api/setup/save-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configs": {
                                    "spring.elasticsearch.uris": "http://x",
                                    "APP_SITE_BEIAN": "t",
                                    "APP_SECRET": "s"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void saveConfig_shouldReturn500_whenSaveThrows() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(systemConfigurationService)
                .saveConfig(anyString(), anyString(), anyBoolean(), anyString());

        mockMvc.perform(post("/api/setup/save-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configs": {
                                    "APP_SECRET": "s"
                                  }
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to save config"));
    }

    @Test
    void complete_shouldDelegateToAuthController() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("admin@example.invalid");
        req.setPassword("pass1234");
        req.setUsername("admin");

                doReturn(ResponseEntity.ok(Map.of("ok", true)))
                        .when(authController)
                        .registerInitialAdmin(any(RegisterRequest.class));

        mockMvc.perform(post("/api/setup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@example.invalid",
                                  "password": "pass1234",
                                  "username": "admin"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

            verify(accessLogKafkaLifecycleManager).startAccessLogEsSinkConsumerIfEnabled();
    }

    @Test
    void complete_shouldReturn400_whenInvalid() throws Exception {
        mockMvc.perform(post("/api/setup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "password": "",
                                  "username": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authController, never()).registerInitialAdmin(any(RegisterRequest.class));
    }

    @Test
    void generateTotp_shouldReturn200_andKeyIsBase64_32Bytes() throws Exception {
        String body = mockMvc.perform(post("/api/setup/generate-totp"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
        String key = (String) parsed.get("key");
        assertNotNull(key);
        byte[] decoded = Base64.getDecoder().decode(key);
        assertEquals(32, decoded.length);
    }

    @Test
    void initIndices_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/setup/init-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Index initialization configuration saved."));
    }

        @Test
        void initIndices_shouldProvisionAccessLogIndexViaDedicatedService() throws Exception {
                mockMvc.perform(post("/api/setup/init-indices")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("""
                                                                {
                                                                    "indexNames": ["access-logs-v1"],
                                                                    "configs": {
                                                                        "spring.elasticsearch.uris": "http://localhost:9200",
                                                                        "APP_ES_API_KEY": "k",
                                                                        "app.logging.access.es-sink.index": "access-logs-v1"
                                                                    }
                                                                }
                                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.results['access-logs-v1']").value("已创建"));

                verify(accessLogEsIndexProvisioningService).initialize(eq("http://localhost:9200"), eq("k"), any());
                verify(restClientFactory, never()).create(anyString(), nullable(String.class));
        }

    @Test
    void setupEndpoints_shouldReturn403_whenSetupAlreadyCompleted() throws Exception {
        when(adminSetupManager.isSetupRequired()).thenReturn(false);

        mockMvc.perform(get("/api/setup/check-env"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Setup already completed"));

        mockMvc.perform(post("/api/setup/generate-totp"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Setup already completed"));
    }

    @Test
    void checkEnv_shouldReturnExistsFalse_whenEnvIsDirectory(@TempDir Path tmp) throws Exception {
        System.setProperty("user.dir", tmp.toString());
        Files.createDirectory(tmp.resolve(".env"));

        mockMvc.perform(get("/api/setup/check-env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    void checkEnv_shouldFindEnvViaJarLocation_whenUserDirMisses(@TempDir Path tmp) throws Exception {
        System.setProperty("user.dir", tmp.toString());

        String rawPath = SetupController.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String sanitized = SetupController.sanitizeCodeSourcePath(rawPath);
        File jarFile = new File(java.net.URLDecoder.decode(sanitized, java.nio.charset.StandardCharsets.UTF_8.name()));
        File startDir = SetupController.resolveStartDir(jarFile);
        assertNotNull(startDir);

        Path envPath = startDir.toPath().resolve(".env");
        Files.writeString(envPath, "Y=2", StandardCharsets.UTF_8);
        try {
            mockMvc.perform(get("/api/setup/check-env"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        } finally {
            Files.deleteIfExists(envPath);
        }
    }

    @Test
    void helpers_shouldSanitizeCodeSourcePath() {
        assertEquals("/a/b", SetupController.sanitizeCodeSourcePath("nested:/a/b"));
        assertEquals("/a/b", SetupController.sanitizeCodeSourcePath("file:/a/b"));
        assertEquals("/a/b.jar", SetupController.sanitizeCodeSourcePath("/a/b.jar!/BOOT-INF/classes!/"));
        assertEquals("/a/b", SetupController.sanitizeCodeSourcePath("/a/b"));
    }

    @Test
    void helpers_shouldResolveStartDir(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        File file = tmp.resolve("x.txt").toFile();
        Files.writeString(file.toPath(), "x", StandardCharsets.UTF_8);

        assertEquals(dir.getAbsolutePath(), SetupController.resolveStartDir(dir).getAbsolutePath());
        assertEquals(dir.getAbsolutePath(), SetupController.resolveStartDir(file).getAbsolutePath());
    }

    @Test
    void helpers_shouldSearchFromStartDir() throws Exception {
        Path tmp = Files.createTempDirectory("sc-test");
        try {
            File startDir = tmp.toFile();
            assertFalse(SetupController.shouldSearchFromStartDir(null, tmp.toString()));
            assertFalse(SetupController.shouldSearchFromStartDir(startDir, startDir.getAbsolutePath()));
            assertTrue(SetupController.shouldSearchFromStartDir(startDir, startDir.getParentFile().getAbsolutePath()));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void helpers_isExistingFile() throws Exception {
        Path tmp = Files.createTempDirectory("sc-test2");
        try {
            File dir = tmp.toFile();
            File file = tmp.resolve("f.txt").toFile();
            Files.writeString(file.toPath(), "x", StandardCharsets.UTF_8);

            assertFalse(SetupController.isExistingFile(null));
            assertFalse(SetupController.isExistingFile(new File(tmp.toString(), "missing.txt")));
            assertFalse(SetupController.isExistingFile(dir));
            assertTrue(SetupController.isExistingFile(file));
        } finally {
            Files.deleteIfExists(tmp.resolve("f.txt"));
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void searchUpwards_shouldCoverEdgePaths(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("r");
        Path l1 = root.resolve("l1");
        Path l2 = l1.resolve("l2");
        Path l3 = l2.resolve("l3");
        Path l4 = l3.resolve("l4");
        Path l5 = l4.resolve("l5");
        Files.createDirectories(l5);

        Files.writeString(l2.resolve(".env"), "Z=9", StandardCharsets.UTF_8);

        File found = ReflectionTestUtils.invokeMethod(setupController, "searchUpwards", l5.toFile());
        assertNotNull(found);
        assertEquals(l2.resolve(".env").toFile().getAbsolutePath(), found.getAbsolutePath());

        Files.deleteIfExists(l2.resolve(".env"));
        Files.writeString(root.resolve(".env"), "Z=9", StandardCharsets.UTF_8);
        File notFound = ReflectionTestUtils.invokeMethod(setupController, "searchUpwards", l5.toFile());
        assertEquals(null, notFound);
    }

    @Test
    void searchUpwards_shouldHandleStartDirNullMissingAndFile(@TempDir Path tmp) throws Exception {
        File nullResult = ReflectionTestUtils.invokeMethod(setupController, "searchUpwards", new Object[]{null});
        assertEquals(null, nullResult);

        File missing = tmp.resolve("missing").toFile();
        File missingResult = ReflectionTestUtils.invokeMethod(setupController, "searchUpwards", missing);
        assertEquals(null, missingResult);

        Path filePath = tmp.resolve("x.txt");
        Files.writeString(filePath, "x", StandardCharsets.UTF_8);
        File fileResult = ReflectionTestUtils.invokeMethod(setupController, "searchUpwards", filePath.toFile());
        assertEquals(null, fileResult);
    }

    @Test
    void searchUpwards_shouldBreakWhenParentBecomesNull() throws Exception {
        Path tmp = Files.createTempDirectory("sc-root");
        try {
            Path driveRoot = tmp.getRoot();
            assertNotNull(driveRoot);
            File out = ReflectionTestUtils.invokeMethod(setupController, "searchUpwards", driveRoot.toFile());
            assertEquals(null, out);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void createClient_shouldFallbackToBuildClient_whenFactoryNull() throws Exception {
        Object original = ReflectionTestUtils.getField(setupController, "restClientFactory");
        try {
            ReflectionTestUtils.setField(setupController, "restClientFactory", null);

            RestClient c1 = ReflectionTestUtils.invokeMethod(setupController, "createClient", "http://localhost:9200", "k");
            assertNotNull(c1);
            c1.close();

            RestClient c2 = ReflectionTestUtils.invokeMethod(setupController, "createClient", "http://localhost:9200", "   ");
            assertNotNull(c2);
            c2.close();

            RestClient c3 = ReflectionTestUtils.invokeMethod(setupController, "createClient", "http://localhost:9200", null);
            assertNotNull(c3);
            c3.close();
        } finally {
            ReflectionTestUtils.setField(setupController, "restClientFactory", original);
        }
    }

    @Test
    void saveConfig_shouldMapImageStorageKeys_andEncryptRules() throws Exception {
        mockMvc.perform(post("/api/setup/save-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configs": {
                                    "IMAGE_STORAGE_MODE": "oss",
                                    "IMAGE_STORAGE_OSS_ENDPOINT": "e",
                                    "IMAGE_STORAGE_OSS_BUCKET": "b",
                                    "IMAGE_STORAGE_OSS_ACCESS_KEY_ID": "id",
                                    "IMAGE_STORAGE_OSS_ACCESS_KEY_SECRET": "sec",
                                    "IMAGE_STORAGE_OSS_REGION": "r",
                                    "IMAGE_STORAGE_WHATEVER": "x",
                                    "spring.elasticsearch.uris": "http://x",
                                    "spring.kafka.bootstrap-servers": "127.0.0.1:9092",
                                    "app.logging.access.kafka-topic": "access-logs-v1",
                                    "app.logging.access.sink-mode": "DUAL",
                                    "APP_KAFKA_AUTH_ENABLED": "true",
                                    "APP_KAFKA_SECURITY_PROTOCOL": "SASL_SSL",
                                    "APP_KAFKA_SASL_MECHANISM": "PLAIN",
                                    "APP_KAFKA_API_KEY": "k-api",
                                    "APP_KAFKA_API_SECRET": "k-secret",
                                    "APP_MAIL_HOST": "smtp",
                                    "APP_MAIL_PORT": "25",
                                    "APP_MAIL_FROM_ADDRESS": "from@example.invalid",
                                    "APP_SITE_COPYRIGHT": "©2026 Test 版权所有",
                                    "APP_SITE_BEIAN": "ICP",
                                    "APP_SITE_BEIAN_HREF": "https://example.invalid",
                                    "APP_SECRET": "s"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(systemConfigurationService).saveConfig(eq("image.storage.mode"), eq("oss"), eq(false), anyString());
        verify(systemConfigurationService, never()).saveConfig(eq("image.storage.dashscope.model"), anyString(), anyBoolean(), anyString());
        verify(systemConfigurationService).saveConfig(eq("image.storage.oss.endpoint"), eq("e"), eq(true), anyString());
        verify(systemConfigurationService).saveConfig(eq("image.storage.oss.bucket"), eq("b"), eq(false), anyString());
        verify(systemConfigurationService).saveConfig(eq("image.storage.oss.access_key_id"), eq("id"), eq(true), anyString());
        verify(systemConfigurationService).saveConfig(eq("image.storage.oss.access_key_secret"), eq("sec"), eq(true), anyString());
        verify(systemConfigurationService).saveConfig(eq("image.storage.oss.region"), eq("r"), eq(false), anyString());
        verify(systemConfigurationService, never()).saveConfig(eq("IMAGE_STORAGE_WHATEVER"), anyString(), anyBoolean(), anyString());

        verify(systemConfigurationService).saveConfig(eq("spring.elasticsearch.uris"), eq("http://x"), eq(false), anyString());
    verify(systemConfigurationService).saveConfig(eq("spring.kafka.bootstrap-servers"), eq("127.0.0.1:9092"), eq(false), anyString());
    verify(systemConfigurationService).saveConfig(eq("app.logging.access.kafka-topic"), eq("access-logs-v1"), eq(false), anyString());
    verify(systemConfigurationService).saveConfig(eq("app.logging.access.sink-mode"), eq("DUAL"), eq(false), anyString());
    verify(systemConfigurationService).saveConfig(eq("APP_KAFKA_AUTH_ENABLED"), eq("true"), eq(false), anyString());
    verify(systemConfigurationService).saveConfig(eq("APP_KAFKA_SECURITY_PROTOCOL"), eq("SASL_SSL"), eq(false), anyString());
    verify(systemConfigurationService).saveConfig(eq("APP_KAFKA_SASL_MECHANISM"), eq("PLAIN"), eq(false), anyString());
    verify(systemConfigurationService).saveConfig(eq("APP_KAFKA_API_KEY"), eq("k-api"), eq(true), anyString());
    verify(systemConfigurationService).saveConfig(eq("APP_KAFKA_API_SECRET"), eq("k-secret"), eq(true), anyString());
        verify(systemConfigurationService).saveConfig(eq("APP_MAIL_HOST"), eq("smtp"), eq(false), anyString());
        verify(systemConfigurationService).saveConfig(eq("APP_MAIL_PORT"), eq("25"), eq(false), anyString());
        verify(systemConfigurationService).saveConfig(eq("APP_MAIL_FROM_ADDRESS"), eq("from@example.invalid"), eq(false), anyString());
        verify(systemConfigurationService).saveConfig(eq("APP_SITE_COPYRIGHT"), eq("©2026 Test 版权所有"), eq(false), anyString());
        verify(systemConfigurationService).saveConfig(eq("APP_SITE_BEIAN"), eq("ICP"), eq(false), anyString());
        verify(systemConfigurationService).saveConfig(eq("APP_SITE_BEIAN_HREF"), eq("https://example.invalid"), eq(false), anyString());
        verify(systemConfigurationService).saveConfig(eq("APP_SECRET"), eq("s"), eq(true), anyString());

        verify(elasticsearchIndexStartupInitializer).init();
    }

    @Test
    void checkEnv_shouldReturnExistsFalse_whenStartDirEqualsUserDir() throws Exception {
        String rawPath = SetupController.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String sanitized = SetupController.sanitizeCodeSourcePath(rawPath);
        File jarFile = new File(java.net.URLDecoder.decode(sanitized, java.nio.charset.StandardCharsets.UTF_8.name()));
        File startDir = SetupController.resolveStartDir(jarFile);
        assertNotNull(startDir);

        Path envPath = startDir.toPath().resolve(".env");
        Files.deleteIfExists(envPath);
        System.setProperty("user.dir", startDir.getAbsolutePath());

        mockMvc.perform(get("/api/setup/check-env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    void searchUpwards_shouldIncludeFileListTruncationBranch(@TempDir Path tmp) throws Exception {
        for (int i = 0; i < 21; i++) {
            Files.writeString(tmp.resolve("f-" + i + ".txt"), "x", StandardCharsets.UTF_8);
        }
        File out = ReflectionTestUtils.invokeMethod(setupController, "searchUpwards", tmp.toFile());
        assertEquals(null, out);
    }
}
