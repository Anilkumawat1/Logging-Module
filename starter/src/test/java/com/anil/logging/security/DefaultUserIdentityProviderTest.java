package com.anil.logging.security;

import com.anil.logging.config.LoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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

    // ── JWT claim extraction ──────────────────────────────────────────────────

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
    void readsUserIdFromJwtSubClaimWhenNoExplicitUserIdClaim() {
        // JWT has only the standard 'sub' claim — should be used as userId fallback
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("user-sub-123")
                .claim("roles", List.of("USER"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, "token", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        // 'sub' is in the default userIdClaims list so it should be resolved from claims
        assertThat(identity.userId()).isEqualTo("user-sub-123");
        assertThat(identity.roles()).containsExactly("USER");
        assertThat(identity.authenticated()).isTrue();
    }

    @Test
    void readsRolesFromOAuth2SpaceDelimitedScopeClaim() {
        // OAuth2 JWTs often carry scopes as a single space-separated string
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("user_id", "svc-account")
                .claim("scope", "read write admin")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, "token", List.of()));

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.userId()).isEqualTo("svc-account");
        // Space-separated scope string should be split into individual role values
        assertThat(identity.roles()).containsExactlyInAnyOrder("read", "write", "admin");
        assertThat(identity.authenticated()).isTrue();
    }

    @Test
    void readsRolesFromOAuth2CommaSeparatedScopeClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("user_id", "svc-account-2")
                .claim("scp", "order:read,order:write")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, "token", List.of()));

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.userId()).isEqualTo("svc-account-2");
        assertThat(identity.roles()).containsExactlyInAnyOrder("order:read", "order:write");
    }

    // ── Fallback to Spring Security authorities ───────────────────────────────

    @Test
    void fallsBackToAuthoritiesWhenTokenClaimsAreMissing() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fallback-user", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.userId()).isEqualTo("fallback-user");
        // ROLE_ prefix should be stripped
        assertThat(identity.roles()).containsExactly("ADMIN");
    }

    @Test
    void stripsRolePrefixFromGrantedAuthorities() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_VIEWER"),
                                new SimpleGrantedAuthority("ROLE_EDITOR"),
                                new SimpleGrantedAuthority("NO_PREFIX_ROLE"))));

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.roles()).containsExactlyInAnyOrder("VIEWER", "EDITOR", "NO_PREFIX_ROLE");
    }

    // ── Anonymous / unauthenticated scenarios ─────────────────────────────────

    @Test
    void returnsAnonymousWhenNoAuthenticationInContext() {
        // SecurityContext is empty (no authentication set)
        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.authenticated()).isFalse();
        assertThat(identity.userId()).isNull();
        assertThat(identity.roles()).isEmpty();
    }

    @Test
    void returnsAnonymousForAnonymousAuthenticationToken() {
        // Spring Security sets an AnonymousAuthenticationToken for public endpoints —
        // isAuthenticated() returns true but we must treat this as unauthenticated.
        AnonymousAuthenticationToken anonymousToken = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymousToken);

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.authenticated()).isFalse();
        assertThat(identity.userId()).isNull();
        assertThat(identity.roles()).isEmpty();
    }

    @Test
    void returnsAnonymousWhenAuthenticationIsNotAuthenticated() {
        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken("user", "password");
        // setAuthenticated(false) simulates a token that hasn't been processed yet
        unauthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);

        UserIdentity identity = provider.getCurrentUser().orElseThrow();

        assertThat(identity.authenticated()).isFalse();
        assertThat(identity.userId()).isNull();
    }

    // ── Custom claim name configuration ──────────────────────────────────────

    @Test
    void respectsCustomUserIdClaimName() {
        LoggingProperties customProps = new LoggingProperties();
        customProps.getIdentity().setUserIdClaims(List.of("custom_user_field"));
        DefaultUserIdentityProvider customProvider = new DefaultUserIdentityProvider(customProps);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("custom_user_field", "custom-user-999")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, "token", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        UserIdentity identity = customProvider.getCurrentUser().orElseThrow();

        assertThat(identity.userId()).isEqualTo("custom-user-999");
    }
}
