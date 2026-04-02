package com.example.EnterpriseRagCommunity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IpPathRateLimitFilterTest {

    @Test
    void blocksWhenSensitivePathExceedsThreshold() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardHeaders", false);
        IpPathRateLimitFilter filter = new IpPathRateLimitFilter(resolver, new ObjectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "windowSeconds", 120);
        ReflectionTestUtils.setField(filter, "maxRequestsPerWindow", 10);
        ReflectionTestUtils.setField(filter, "sensitiveMaxRequestsPerWindow", 1);
        ReflectionTestUtils.setField(filter, "setupMaxRequestsPerWindow", 10);
        ReflectionTestUtils.setField(filter, "initialAdminMaxRequestsPerWindow", 5);
        ReflectionTestUtils.setField(filter, "sensitivePathPrefixesRaw", "/api/auth");
        ReflectionTestUtils.setField(filter, "cleanupIntervalSeconds", 120);

        AtomicInteger passed = new AtomicInteger(0);
        FilterChain chain = (req, resp) -> passed.incrementAndGet();

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilter(req1, resp1, chain);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilter(req2, resp2, chain);

        assertThat(passed.get()).isEqualTo(1);
        assertThat(resp1.getStatus()).isEqualTo(200);
        assertThat(resp2.getStatus()).isEqualTo(429);
        assertThat(resp2.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void allowsWithinGenericThreshold() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardHeaders", false);
        IpPathRateLimitFilter filter = new IpPathRateLimitFilter(resolver, new ObjectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "windowSeconds", 120);
        ReflectionTestUtils.setField(filter, "maxRequestsPerWindow", 2);
        ReflectionTestUtils.setField(filter, "sensitiveMaxRequestsPerWindow", 1);
        ReflectionTestUtils.setField(filter, "setupMaxRequestsPerWindow", 10);
        ReflectionTestUtils.setField(filter, "initialAdminMaxRequestsPerWindow", 5);
        ReflectionTestUtils.setField(filter, "sensitivePathPrefixesRaw", "/api/auth");
        ReflectionTestUtils.setField(filter, "cleanupIntervalSeconds", 120);

        AtomicInteger passed = new AtomicInteger(0);
        FilterChain chain = (req, resp) -> passed.incrementAndGet();

        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/posts/1");
        req1.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilter(req1, resp1, chain);

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/posts/2");
        req2.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilter(req2, resp2, chain);

        assertThat(passed.get()).isEqualTo(2);
        assertThat(resp1.getStatus()).isEqualTo(200);
        assertThat(resp2.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksWhenSetupPathExceedsDedicatedThreshold() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardHeaders", false);
        IpPathRateLimitFilter filter = new IpPathRateLimitFilter(resolver, new ObjectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "windowSeconds", 120);
        ReflectionTestUtils.setField(filter, "maxRequestsPerWindow", 10);
        ReflectionTestUtils.setField(filter, "sensitiveMaxRequestsPerWindow", 10);
        ReflectionTestUtils.setField(filter, "setupMaxRequestsPerWindow", 1);
        ReflectionTestUtils.setField(filter, "initialAdminMaxRequestsPerWindow", 5);
        ReflectionTestUtils.setField(filter, "sensitivePathPrefixesRaw", "/api/auth");
        ReflectionTestUtils.setField(filter, "cleanupIntervalSeconds", 120);

        AtomicInteger passed = new AtomicInteger(0);
        FilterChain chain = (req, resp) -> passed.incrementAndGet();

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/setup/test-es");
        req1.setRemoteAddr("7.7.7.7");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilter(req1, resp1, chain);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/setup/test-es");
        req2.setRemoteAddr("7.7.7.7");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilter(req2, resp2, chain);

        assertThat(passed.get()).isEqualTo(1);
        assertThat(resp1.getStatus()).isEqualTo(200);
        assertThat(resp2.getStatus()).isEqualTo(429);
    }

    @Test
    void blocksWhenInitialAdminPathExceedsDedicatedThreshold() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardHeaders", false);
        IpPathRateLimitFilter filter = new IpPathRateLimitFilter(resolver, new ObjectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "windowSeconds", 120);
        ReflectionTestUtils.setField(filter, "maxRequestsPerWindow", 10);
        ReflectionTestUtils.setField(filter, "sensitiveMaxRequestsPerWindow", 10);
        ReflectionTestUtils.setField(filter, "setupMaxRequestsPerWindow", 10);
        ReflectionTestUtils.setField(filter, "initialAdminMaxRequestsPerWindow", 1);
        ReflectionTestUtils.setField(filter, "sensitivePathPrefixesRaw", "/api/auth");
        ReflectionTestUtils.setField(filter, "cleanupIntervalSeconds", 120);

        AtomicInteger passed = new AtomicInteger(0);
        FilterChain chain = (req, resp) -> passed.incrementAndGet();

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/register-initial-admin");
        req1.setRemoteAddr("6.6.6.6");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilter(req1, resp1, chain);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/register-initial-admin");
        req2.setRemoteAddr("6.6.6.6");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilter(req2, resp2, chain);

        assertThat(passed.get()).isEqualTo(1);
        assertThat(resp1.getStatus()).isEqualTo(200);
        assertThat(resp2.getStatus()).isEqualTo(429);
    }
}
