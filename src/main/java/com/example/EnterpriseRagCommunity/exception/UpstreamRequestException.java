package com.example.EnterpriseRagCommunity.exception;

import org.springframework.http.HttpStatus;

public class UpstreamRequestException extends RuntimeException {
    private final HttpStatus status;

    public UpstreamRequestException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public UpstreamRequestException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
