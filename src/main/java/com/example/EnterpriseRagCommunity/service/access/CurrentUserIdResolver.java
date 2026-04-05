package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;

import java.util.function.Supplier;

public final class CurrentUserIdResolver {

    private CurrentUserIdResolver() {
    }

    public static Long currentUserIdOrNull(AdministratorService administratorService) {
        try {
            String username = CurrentUsernameResolver.currentUsernameOrNull();
            if (username == null) {
                return null;
            }
            return administratorService.findByUsername(username)
                    .map(UsersEntity::getId)
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    public static Long currentUserIdOrThrow(
            AdministratorService administratorService,
            Supplier<? extends RuntimeException> unauthenticatedExceptionSupplier,
            Supplier<? extends RuntimeException> missingUserExceptionSupplier
    ) {
        String username = CurrentUsernameResolver.currentUsernameOrNull();
        if (username == null) {
            throw unauthenticatedExceptionSupplier.get();
        }
        return administratorService.findByUsername(username)
                .map(UsersEntity::getId)
                .orElseThrow(missingUserExceptionSupplier);
    }
}
