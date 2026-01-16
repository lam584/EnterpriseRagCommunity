package com.example.EnterpriseRagCommunity.controller.debug;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Dev-only endpoint to quickly inspect the current session's Authentication.
 */
@RestController
@RequestMapping("/api/debug")
@Profile("dev")
public class WhoAmIController {

    @GetMapping("/whoami")
    public ResponseEntity<?> whoami(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(new WhoAmIResponse(null, false, List.of()));
        }

        List<String> authorities = authentication.getAuthorities().stream()
                .filter(Objects::nonNull)
                .map(GrantedAuthority::getAuthority)
                .toList();

        return ResponseEntity.ok(new WhoAmIResponse(authentication.getName(), authentication.isAuthenticated(), authorities));
    }

    private record WhoAmIResponse(String name, boolean authenticated, List<String> authorities) {
    }
}

