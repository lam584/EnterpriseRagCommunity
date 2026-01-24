package com.example.EnterpriseRagCommunity.service.notify;

public record EmailTransportConfig(
        String protocol,
        String host,
        int port,
        EmailEncryption encryption,
        int connectTimeoutMs,
        int timeoutMs,
        int writeTimeoutMs,
        boolean debug,
        String sslTrust
) {
}
