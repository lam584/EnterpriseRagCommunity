package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.response.AccessContextResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AccessContextController {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String ROLE_ID_PREFIX = "ROLE_ID_";
    private static final String PERM_PREFIX = "PERM_";

    @GetMapping("/access-context")
    public ResponseEntity<AccessContextResponse> accessContext(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(new AccessContextResponse(null, Collections.emptyList(), Collections.emptyList()));
        }

        String email = authentication.getName();

        // We accept both styles:
        // - ROLE_ADMIN / PERM_post:write (preferred)
        // - ADMIN / post:write (legacy or alternative producers)
        List<String> roles = authentication.getAuthorities().stream()
                .filter(Objects::nonNull)
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(a -> !a.isBlank())
                .filter(a -> !a.startsWith(ROLE_ID_PREFIX))
                .filter(a -> a.startsWith(ROLE_PREFIX) || !a.contains(":"))
                .map(a -> a.startsWith(ROLE_PREFIX) ? a.substring(ROLE_PREFIX.length()) : a)
                .distinct()
                .toList();

        List<String> permissions = authentication.getAuthorities().stream()
                .filter(Objects::nonNull)
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(a -> !a.isBlank())
                .filter(a -> a.startsWith(PERM_PREFIX) || a.contains(":"))
                .map(a -> a.startsWith(PERM_PREFIX) ? a.substring(PERM_PREFIX.length()) : a)
                .distinct()
                .toList();

        return ResponseEntity.ok(new AccessContextResponse(email, roles, permissions));
    }
}
