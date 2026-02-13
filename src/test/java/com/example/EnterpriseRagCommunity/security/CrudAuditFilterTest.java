package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogContextHolder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class CrudAuditFilterTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        AuditLogContextHolder.clear();
    }

    @Test
    void writesAuditLogForPostWhenAuthenticated() throws Exception {
        AuditLogWriter auditLogWriter = Mockito.mock(AuditLogWriter.class);
        AdministratorService administratorService = Mockito.mock(AdministratorService.class);

        CrudAuditFilter filter = new CrudAuditFilter(auditLogWriter, administratorService);
        ReflectionTestUtils.setField(filter, "autoCrudEnabled", true);
        ReflectionTestUtils.setField(filter, "includeReads", false);
        ReflectionTestUtils.setField(filter, "includePathPrefixesRaw", "");
        ReflectionTestUtils.setField(filter, "excludePathPrefixesRaw", "");

        UsersEntity u = new UsersEntity();
        u.setId(7L);
        Mockito.when(administratorService.findByUsername("user@example.com")).thenReturn(Optional.of(u));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user@example.com", "pw", "ROLE_ADMIN"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/test/items/123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> resp.setStatus(200);

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entityTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> entityIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<AuditResult> resultCaptor = ArgumentCaptor.forClass(AuditResult.class);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);

        Mockito.verify(auditLogWriter, Mockito.times(1))
                .write(
                        Mockito.eq(7L),
                        Mockito.eq("user@example.com"),
                        actionCaptor.capture(),
                        entityTypeCaptor.capture(),
                        entityIdCaptor.capture(),
                        resultCaptor.capture(),
                        Mockito.anyString(),
                        Mockito.isNull(),
                        detailsCaptor.capture()
                );

        assertThat(actionCaptor.getValue()).isEqualTo("CRUD_CREATE");
        assertThat(entityTypeCaptor.getValue()).isEqualTo("TEST");
        assertThat(entityIdCaptor.getValue()).isEqualTo(123L);
        assertThat(resultCaptor.getValue()).isEqualTo(AuditResult.SUCCESS);
        assertThat(detailsCaptor.getValue()).containsEntry("autoCrud", true).containsEntry("path", "/api/test/items/123");
    }

    @Test
    void doesNotWriteAuditLogForGetByDefault() throws Exception {
        AuditLogWriter auditLogWriter = Mockito.mock(AuditLogWriter.class);
        AdministratorService administratorService = Mockito.mock(AdministratorService.class);

        CrudAuditFilter filter = new CrudAuditFilter(auditLogWriter, administratorService);
        ReflectionTestUtils.setField(filter, "autoCrudEnabled", true);
        ReflectionTestUtils.setField(filter, "includeReads", false);
        ReflectionTestUtils.setField(filter, "includePathPrefixesRaw", "");
        ReflectionTestUtils.setField(filter, "excludePathPrefixesRaw", "");

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user@example.com", "pw", "ROLE_ADMIN"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test/items/123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> resp.setStatus(200);

        filter.doFilter(req, resp, chain);

        Mockito.verifyNoInteractions(auditLogWriter);
    }

    @Test
    void skipsAutoAuditWhenManualAuditAlreadyWritten() throws Exception {
        AuditLogWriter auditLogWriter = Mockito.mock(AuditLogWriter.class);
        AdministratorService administratorService = Mockito.mock(AdministratorService.class);

        CrudAuditFilter filter = new CrudAuditFilter(auditLogWriter, administratorService);
        ReflectionTestUtils.setField(filter, "autoCrudEnabled", true);
        ReflectionTestUtils.setField(filter, "includeReads", false);
        ReflectionTestUtils.setField(filter, "includePathPrefixesRaw", "");
        ReflectionTestUtils.setField(filter, "excludePathPrefixesRaw", "");

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user@example.com", "pw", "ROLE_ADMIN"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/test/items/1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> {
            AuditLogContextHolder.markWritten();
            resp.setStatus(200);
        };

        filter.doFilter(req, resp, chain);

        Mockito.verifyNoInteractions(auditLogWriter);
    }

    @Test
    void writesAuditLogForGetWhenIncludeReadsAndWhitelisted() throws Exception {
        AuditLogWriter auditLogWriter = Mockito.mock(AuditLogWriter.class);
        AdministratorService administratorService = Mockito.mock(AdministratorService.class);

        CrudAuditFilter filter = new CrudAuditFilter(auditLogWriter, administratorService);
        ReflectionTestUtils.setField(filter, "autoCrudEnabled", true);
        ReflectionTestUtils.setField(filter, "includeReads", true);
        ReflectionTestUtils.setField(filter, "includePathPrefixesRaw", "/api/admin/");
        ReflectionTestUtils.setField(filter, "excludePathPrefixesRaw", "");

        UsersEntity u = new UsersEntity();
        u.setId(9L);
        Mockito.when(administratorService.findByUsername("admin@example.com")).thenReturn(Optional.of(u));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin@example.com", "pw", "ROLE_ADMIN"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/settings/registration");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> resp.setStatus(200);

        filter.doFilter(req, resp, chain);

        Mockito.verify(auditLogWriter, Mockito.times(1))
                .write(
                        Mockito.eq(9L),
                        Mockito.eq("admin@example.com"),
                        Mockito.eq("CRUD_READ"),
                        Mockito.eq("ADMIN_SETTINGS"),
                        Mockito.isNull(),
                        Mockito.eq(AuditResult.SUCCESS),
                        Mockito.anyString(),
                        Mockito.isNull(),
                        Mockito.anyMap()
                );
    }
}

