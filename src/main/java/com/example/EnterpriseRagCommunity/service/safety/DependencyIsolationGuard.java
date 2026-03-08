package com.example.EnterpriseRagCommunity.service.safety;

import com.example.EnterpriseRagCommunity.exception.UpstreamRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DependencyIsolationGuard {

    private final ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    public void requireElasticsearchAllowed() {
        if (contentSafetyCircuitBreakerService.isModeS3WithElasticsearchIsolation()) {
            throw new UpstreamRequestException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 已被依赖隔离");
        }
    }

    public void requireMysqlAllowed() {
        if (contentSafetyCircuitBreakerService.isModeS3WithMysqlIsolation()) {
            throw new UpstreamRequestException(HttpStatus.SERVICE_UNAVAILABLE, "MySQL 已被依赖隔离");
        }
    }
}

