package com.example.EnterpriseRagCommunity.controller.debug;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhoAmIControllerBranchTest {

    @Test
    void whoami_shouldReturnAnonymousWhenAuthenticationMissing() {
        WhoAmIController controller = new WhoAmIController();

        ResponseEntity<?> response = controller.whoami(null);

        assertEquals(200, response.getStatusCode().value());
        Object body = response.getBody();
        assertNotNull(body);
        assertEquals("WhoAmIResponse[name=null, authenticated=false, authorities=[]]", body.toString());
    }

    @Test
    void whoami_shouldReturnNameAndAuthoritiesWhenAuthenticated() {
        WhoAmIController controller = new WhoAmIController();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "tester@example.com",
                "p",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), (GrantedAuthority) () -> null)
        );

        ResponseEntity<?> response = controller.whoami(authentication);

        assertEquals(200, response.getStatusCode().value());
        Object body = response.getBody();
        assertNotNull(body);
        assertEquals("WhoAmIResponse[name=tester@example.com, authenticated=true, authorities=[ROLE_USER, null]]", body.toString());
    }
}
