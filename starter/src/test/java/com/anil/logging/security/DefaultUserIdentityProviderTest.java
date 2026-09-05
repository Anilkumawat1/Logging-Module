package com.anil.logging.security;

import com.anil.logging.config.LoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultUserIdentityProviderTest {
    private final DefaultUserIdentityProvider provider = new DefaultUserIdentityProvider(new LoggingProperties());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsUserIdAndRoleIdsFromJwtClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("user_id", "1001")
                .claim("role_ids", List.of("ROLE_ADMIN_ID", "ROLE_MANAGER_ID"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, "token", List.of(new SimpleGrantedAuthority("ROLE_FALLBACK"))));

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.userId()).isEqualTo("1001");
        assertThat(identity.roles()).containsExactly("ROLE_ADMIN_ID", "ROLE_MANAGER_ID");
        assertThat(identity.authenticated()).isTrue();
    }

    @Test
    void fallsBackToAuthoritiesWhenTokenClaimsAreMissing() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fallback-user", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.userId()).isEqualTo("fallback-user");
        assertThat(identity.roles()).containsExactly("ADMIN");
    }
}
