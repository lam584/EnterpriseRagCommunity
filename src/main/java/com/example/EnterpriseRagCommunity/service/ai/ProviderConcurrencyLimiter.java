package com.example.EnterpriseRagCommunity.service.ai;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class ProviderConcurrencyLimiter {

    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    public interface CheckedRunnable {
        void run() throws Exception;
    }

    private record Limiter(int maxConcurrent, Semaphore semaphore) {
    }

    private final ConcurrentHashMap<String, Limiter> limiters = new ConcurrentHashMap<>();

    public <T> T call(String providerId, Integer maxConcurrent, CheckedSupplier<T> supplier) throws Exception {
        if (maxConcurrent == null || maxConcurrent <= 0) {
            return supplier.get();
        }

        Limiter limiter = getLimiter(providerId, maxConcurrent);
        if (!limiter.semaphore.tryAcquire()) {
            throw new IllegalStateException("该模型来源已达到并发上限，请稍后重试");
        }
        try {
            return supplier.get();
        } finally {
            limiter.semaphore.release();
        }
    }

    public void run(String providerId, Integer maxConcurrent, CheckedRunnable runnable) throws Exception {
        call(providerId, maxConcurrent, () -> {
            runnable.run();
            return null;
        });
    }

    private Limiter getLimiter(String providerId, int maxConcurrent) {
        String key = (providerId == null || providerId.isBlank()) ? "default" : providerId.trim();
        return limiters.compute(key, (k, existing) -> {
            if (existing == null) return new Limiter(maxConcurrent, new Semaphore(maxConcurrent));
            if (existing.maxConcurrent == maxConcurrent) return existing;
            return new Limiter(maxConcurrent, new Semaphore(maxConcurrent));
        });
    }
}

