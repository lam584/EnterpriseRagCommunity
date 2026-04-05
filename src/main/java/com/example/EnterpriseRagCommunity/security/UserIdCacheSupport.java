package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class UserIdCacheSupport {

    private UserIdCacheSupport() {
    }

    record UserIdCacheEntry(Long userId, long expiresAtMs) {
    }

    static Long resolveUserId(ConcurrentHashMap<String, UserIdCacheEntry> cache,
                              long ttlMs,
                              AdministratorService administratorService,
                              String username) {
        long now = System.currentTimeMillis();
        UserIdCacheEntry cached = cache.get(username);
        if (cached != null && cached.expiresAtMs() > now) return cached.userId();
        Optional<UsersEntity> user = administratorService.findByUsername(username);
        Long id = user.map(UsersEntity::getId).orElse(null);
        cache.put(username, new UserIdCacheEntry(id, now + ttlMs));
        return id;
    }
}
