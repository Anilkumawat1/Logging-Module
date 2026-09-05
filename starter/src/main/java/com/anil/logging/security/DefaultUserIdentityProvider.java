package com.anil.logging.security;

import com.anil.logging.config.LoggingProperties;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultUserIdentityProvider implements UserIdentityProvider {
    private final LoggingProperties properties;

    public DefaultUserIdentityProvider(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<UserIdentity> getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of(UserIdentity.anonymous());
            }
            // AnonymousAuthenticationToken.isAuthenticated() returns true, but the
            // request is effectively unauthenticated — treat it as anonymous.
            if (authentication instanceof AnonymousAuthenticationToken) {
                return Optional.of(UserIdentity.anonymous());
            }
            Map<String, Object> claims = claims(authentication);
            String userId = firstClaimAsString(claims, properties.getIdentity().getUserIdClaims())
                    .orElse(authentication.getName());
            List<String> roleIds = firstClaimAsList(claims, properties.getIdentity().getRoleIdClaims());
            if (roleIds.isEmpty()) {
                roleIds = authorities(authentication);
            }
            return Optional.of(new UserIdentity(userId, roleIds, true));
        } catch (RuntimeException ex) {
            return Optional.of(UserIdentity.anonymous());
        }
    }

    private static Map<String, Object> claims(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getClaims();
        }
        if (principal instanceof Map<?, ?> map) {
            return toStringObjectMap(map);
        }
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map) {
            return toStringObjectMap(map);
        }
        return Map.of();
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private static Optional<String> firstClaimAsString(Map<String, Object> claims, List<String> names) {
        for (String name : names) {
            Object value = claims.get(name);
            if (value != null && !String.valueOf(value).isBlank()) {
                return Optional.of(String.valueOf(value));
            }
        }
        return Optional.empty();
    }

    private static List<String> firstClaimAsList(Map<String, Object> claims, List<String> names) {
        for (String name : names) {
            Object value = claims.get(name);
            List<String> values = toStringList(value);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    private static List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(String::valueOf)
                    .toList();
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<String> result = new ArrayList<>();
            for (int index = 0; index < length; index++) {
                Object item = java.lang.reflect.Array.get(value, index);
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return List.of();
        }
        if (text.contains(" ")) {
            return List.of(text.split("\\s+"));
        }
        if (text.contains(",")) {
            return java.util.Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        return List.of(text);
    }

    private static List<String> authorities(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (role != null && role.startsWith("ROLE_")) {
                role = role.substring(5);
            }
            if (role != null && !role.isBlank()) {
                roles.add(role);
            }
        }
        return List.copyOf(roles);
    }
}
