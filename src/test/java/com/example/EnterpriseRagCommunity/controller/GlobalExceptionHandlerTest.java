package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.exception.UpstreamRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void handleDatabaseExceptions_whenMessageContainsAdministratorId_shouldReturnMissingAdminMessage() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleDatabaseExceptions(
                new RuntimeException("bad fk: administrator_id is null")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "添加图书失败：管理员信息缺失");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unique constraint", "Duplicate entry"})
    void handleDatabaseExceptions_whenUniquenessViolation_shouldReturnAlreadyExistsMessage(String token) {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleDatabaseExceptions(
                new RuntimeException("constraint failed: " + token)
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "操作失败：数据已存在");
    }

    @Test
    void handleDatabaseExceptions_whenOtherMessage_shouldReturnGenericDbMessage() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleDatabaseExceptions(
                new RuntimeException("some db error")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "数据操作失败，请联系系统管理员");
    }

    @Test
    void handleNoResourceFound_shouldReturn404AndIncludeResourcePath() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/missing.js")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "资源未找到: /missing.js");
    }

    @Test
    void handleAuthenticationException_shouldReturn401WithFixedMessage() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleAuthenticationException(
                new AuthenticationException("bad auth") {
                }
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "未登录或登录已过期");
    }

    @Test
    void handleAccessDenied_shouldReturn403WithFixedMessage() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleAccessDenied(
                new AccessDeniedException("denied")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "无权限访问");
    }

    @Test
    void handleIllegalArgument_shouldReturn400AndEchoMessage() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleIllegalArgument(
                new IllegalArgumentException("参数 x 不合法")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "参数 x 不合法");
    }

    @Test
    void handleResourceNotFound_shouldReturn404AndEchoMessage() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleResourceNotFound(
                new ResourceNotFoundException("not found: 1")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "not found: 1");
    }

    @Test
    void handleIllegalState_whenTotpMasterKeyNotConfigured_shouldReturn503() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleIllegalState(
                new IllegalStateException("TOTP master key not configured")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "TOTP master key not configured");
    }

    @Test
    void handleIllegalState_whenOtherMessage_shouldReturn409() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleIllegalState(
                new IllegalStateException("conflict")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "conflict");
    }

    @Test
    void handleIllegalState_whenMessageNull_shouldReturn409() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleIllegalState(
                new IllegalStateException((String) null)
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", null);
    }

    @Test
    void handleResponseStatusException_whenStatusUnresolvable_shouldFallbackTo500AndUseReason() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleResponseStatusException(
                new ResponseStatusException(HttpStatusCode.valueOf(599), "oops")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "oops");
    }

    @Test
    void handleResponseStatusException_whenReasonNull_shouldUseReasonPhrase() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, null)
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", HttpStatus.BAD_REQUEST.getReasonPhrase());
    }

    @Test
    void handleResponseStatusException_whenReasonBlank_shouldUseReasonPhrase() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "   ")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", HttpStatus.FORBIDDEN.getReasonPhrase());
    }

    @Test
    void handleResponseStatusException_whenReasonNonBlank_shouldUseReason() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "nope")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "nope");
    }

    @Test
    void handleClientAbort_shouldReturn204NoContent() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Void> r = h.handleClientAbort(new Exception("client abort"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(r.getBody()).isNull();
    }

    @Test
    void handleMaxUploadSizeExceeded_shouldReturn413WithFixedMessage() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(1024)
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "上传失败：文件或请求体大小超过服务器限制");
    }

    @Test
    void handleUpstreamRequest_whenStatusNull_shouldDefaultToBadGateway() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleUpstreamRequest(
                new UpstreamRequestException(null, "upstream failed")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "upstream failed");
    }

    @Test
    void handleUpstreamRequest_whenStatusProvided_shouldUseProvidedStatus() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> r = h.handleUpstreamRequest(
                new UpstreamRequestException(HttpStatus.SERVICE_UNAVAILABLE, "upstream failed")
        );

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).containsEntry("message", "upstream failed");
    }
}
