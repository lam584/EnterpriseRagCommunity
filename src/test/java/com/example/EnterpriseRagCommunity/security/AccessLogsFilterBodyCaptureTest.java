package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessLogWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessLogsFilterBodyCaptureTest {

    @Test
    void capturesAndMasksJsonRequestAndResponseBodies() throws Exception {
        AccessLogWriter accessLogWriter = Mockito.mock(AccessLogWriter.class);
        AdministratorService administratorService = Mockito.mock(AdministratorService.class);

        AccessLogsFilter filter = new AccessLogsFilter(accessLogWriter, administratorService);
        ReflectionTestUtils.setField(filter, "captureBodyEnabled", true);
        ReflectionTestUtils.setField(filter, "captureResponseBodyEnabled", true);
        ReflectionTestUtils.setField(filter, "maxBodyBytes", 65536);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/test/echo");
        req.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        req.setContent("{\"password\":\"p@ss\",\"token\":\"abc\",\"nested\":{\"secret\":\"s\"}}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (ServletRequest request, ServletResponse response) -> {
            String in = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            assertThat(in).contains("\"password\":\"p@ss\"");
            response.setContentType("application/json");
            response.getOutputStream().write("{\"ok\":true,\"password\":\"respPass\",\"token\":\"respToken\"}".getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilter(req, resp, chain);

        assertThat(resp.getContentAsString()).contains("\"password\":\"respPass\"");

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(accessLogWriter, Mockito.atLeastOnce())
                .write(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("/api/test/echo"),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        detailsCaptor.capture()
                );

        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).containsKeys("reqBody", "resBody");

        @SuppressWarnings("unchecked")
        Map<String, Object> reqBody = (Map<String, Object>) details.get("reqBody");
        @SuppressWarnings("unchecked")
        Map<String, Object> resBody = (Map<String, Object>) details.get("resBody");

        assertThat(String.valueOf(reqBody.get("body"))).doesNotContain("p@ss").contains("\"password\":\"***\"");
        assertThat(String.valueOf(reqBody.get("body"))).doesNotContain("\"token\":\"abc\"").contains("\"token\":\"***\"");

        assertThat(String.valueOf(resBody.get("body"))).doesNotContain("respPass").contains("\"password\":\"***\"");
        assertThat(String.valueOf(resBody.get("body"))).doesNotContain("\"token\":\"respToken\"").contains("\"token\":\"***\"");
    }

    @Test
    void truncatesBodiesByConfiguredLimit() throws Exception {
        AccessLogWriter accessLogWriter = Mockito.mock(AccessLogWriter.class);
        AdministratorService administratorService = Mockito.mock(AdministratorService.class);

        AccessLogsFilter filter = new AccessLogsFilter(accessLogWriter, administratorService);
        ReflectionTestUtils.setField(filter, "captureBodyEnabled", true);
        ReflectionTestUtils.setField(filter, "captureResponseBodyEnabled", true);
        ReflectionTestUtils.setField(filter, "maxBodyBytes", 20);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/test/long");
        req.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        req.setContent("{\"content\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (ServletRequest request, ServletResponse response) -> {
            StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            response.setContentType("application/json");
            response.getOutputStream().write("{\"content\":\"yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy\"}".getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(accessLogWriter, Mockito.atLeastOnce())
                .write(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("/api/test/long"),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        detailsCaptor.capture()
                );

        Map<String, Object> details = detailsCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> reqBody = (Map<String, Object>) details.get("reqBody");
        @SuppressWarnings("unchecked")
        Map<String, Object> resBody = (Map<String, Object>) details.get("resBody");

        assertThat(reqBody.get("truncated")).isEqualTo(true);
        assertThat(resBody.get("truncated")).isEqualTo(true);

        String reqText = String.valueOf(reqBody.get("body"));
        String resText = String.valueOf(resBody.get("body"));
        assertThat(reqText.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(20);
        assertThat(resText.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(20);
    }

    @Test
    void skipsResponseBodyForEventStream() throws Exception {
        AccessLogWriter accessLogWriter = Mockito.mock(AccessLogWriter.class);
        AdministratorService administratorService = Mockito.mock(AdministratorService.class);

        AccessLogsFilter filter = new AccessLogsFilter(accessLogWriter, administratorService);
        ReflectionTestUtils.setField(filter, "captureBodyEnabled", true);
        ReflectionTestUtils.setField(filter, "captureResponseBodyEnabled", true);
        ReflectionTestUtils.setField(filter, "maxBodyBytes", 65536);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test/sse");
        req.addHeader("Accept", "text/event-stream");

        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (ServletRequest request, ServletResponse response) -> {
            response.setContentType("text/event-stream");
            response.getOutputStream().write("data: hi\n\n".getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(accessLogWriter, Mockito.atLeastOnce())
                .write(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("/api/test/sse"),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        detailsCaptor.capture()
                );

        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).doesNotContainKey("resBody");
    }
}
