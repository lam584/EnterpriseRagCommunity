package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.testsupport.MySqlTestcontainersBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiRegressionIntegrationTest extends MySqlTestcontainersBase {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void publicSiteConfig_shouldReturn200() {
        ResponseEntity<String> resp = restTemplate.getForEntity("http://localhost:" + port + "/api/public/site-config", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("beianText");
        assertThat(resp.getBody()).contains("beianHref");
    }

    @Test
    void accountSecurity2faPolicy_shouldReturn401_whenAnonymous() {
        ResponseEntity<String> resp = restTemplate.getForEntity("http://localhost:" + port + "/api/account/security-2fa-policy", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
