package com.example.EnterpriseRagCommunity.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
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

}
