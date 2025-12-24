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

@RestController
@RequestMapping("/api/auth")
public class AccessContextController {

    @GetMapping("/access-context")
    public ResponseEntity<AccessContextResponse> accessContext(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(new AccessContextResponse(null, Collections.emptyList(), Collections.emptyList()));
        }

        String email = authentication.getName();

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .toList();

        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("PERM_"))
                .toList();

        return ResponseEntity.ok(new AccessContextResponse(email, roles, permissions));
    }
}
