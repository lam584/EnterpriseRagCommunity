package com.example.EnterpriseRagCommunity.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardHeadersWhenTrustDisabled() {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardHeaders", false);
        ReflectionTestUtils.setField(resolver, "trustedProxiesRaw", "127.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("10.0.0.8");
    }

    @Test
    void usesForwardHeadersWhenProxyTrusted() {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardHeaders", true);
        ReflectionTestUtils.setField(resolver, "trustedProxiesRaw", "127.0.0.1,10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.1.2.3");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void ignoresForwardHeadersWhenProxyUntrusted() {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardHeaders", true);
        ReflectionTestUtils.setField(resolver, "trustedProxiesRaw", "127.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("10.1.2.3");
    }
}
