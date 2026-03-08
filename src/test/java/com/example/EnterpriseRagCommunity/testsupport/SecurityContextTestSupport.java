package com.example.EnterpriseRagCommunity.testsupport;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextTestSupport {
    private SecurityContextTestSupport() {
    }

    public static Authentication setAuthenticatedEmail(String email) {
        Authentication auth = new TestingAuthenticationToken(email, "n/a");
        if (auth instanceof TestingAuthenticationToken t) {
            t.setAuthenticated(true);
        }
        SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}

