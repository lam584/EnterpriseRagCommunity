package com.example.EnterpriseRagCommunity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatPathBlockFilterTest {

    @Test
    void blocksSuspiciousApiPathWith404() throws Exception {
        ThreatPathBlockFilter filter = new ThreatPathBlockFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "rawPatterns", "/.env,/.git/");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/.env");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, resp) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainCalled.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("Not Found");
        assertThat(request.getAttribute("threatBlocked")).isEqualTo(true);
    }

    @Test
    void allowsNormalPath() throws Exception {
        ThreatPathBlockFilter filter = new ThreatPathBlockFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "rawPatterns", "/.env,/.git/");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/site-config");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, resp) -> {
            chainCalled.set(true);
            ((MockHttpServletResponse) resp).setStatus(200);
        };

        filter.doFilter(request, response, chain);

        assertThat(chainCalled.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
