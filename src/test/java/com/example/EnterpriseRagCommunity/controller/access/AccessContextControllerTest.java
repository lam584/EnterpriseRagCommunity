package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.response.AccessContextResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessContextControllerTest {

    @Test
    void accessContext_should_extract_roles_and_permissions_from_authorities() {
        AccessContextController controller = new AccessContextController();

        var auth = new TestingAuthenticationToken(
                "u@example.com",
                "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_ID_1"),
                        new SimpleGrantedAuthority("PERM_post:write")
                )
        );

        AccessContextResponse body = controller.accessContext(auth).getBody();
        assertThat(body).isNotNull();
        assertThat(body.email()).isEqualTo("u@example.com");

        // API contract: return normalized values (no prefixes)
        assertThat(body.roles()).containsExactly("ADMIN");
        assertThat(body.permissions()).containsExactly("post:write");
    }

    @Test
    void accessContext_when_authentication_null_should_return_empty() {
        AccessContextController controller = new AccessContextController();
        AccessContextResponse body = controller.accessContext(null).getBody();

        assertThat(body).isNotNull();
        assertThat(body.email()).isNull();
        assertThat(body.roles()).isEmpty();
        assertThat(body.permissions()).isEmpty();
    }

    @Test
    void accessContext_should_handle_legacy_authorities_blanks_nulls_and_distinct() {
        AccessContextController controller = new AccessContextController();

        GrantedAuthority nullAuthority = new GrantedAuthority() {
            @Override
            public String getAuthority() {
                return null;
            }
        };

        GrantedAuthority blankAuthority = new GrantedAuthority() {
            @Override
            public String getAuthority() {
                return "  ";
            }
        };

        var auth = new TestingAuthenticationToken(
                "u@example.com",
                "n/a",
            Arrays.asList(
                        nullAuthority,
                        blankAuthority,
                        new SimpleGrantedAuthority("ROLE_ID_2"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ADMIN"),
                        new SimpleGrantedAuthority("post:read"),
                        new SimpleGrantedAuthority("PERM_post:write"),
                        new SimpleGrantedAuthority("PERM_post:write")
                )
        );

        AccessContextResponse body = controller.accessContext(auth).getBody();
        assertThat(body).isNotNull();
        assertThat(body.email()).isEqualTo("u@example.com");
        assertThat(body.roles()).containsExactly("ADMIN");
        assertThat(body.permissions()).containsExactly("post:read", "post:write");
    }
}
