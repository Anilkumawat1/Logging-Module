package com.anil.logging.security;

import java.util.List;

public record UserIdentity(String userId, List<String> roles, boolean authenticated) {
    public static UserIdentity anonymous() {
        return new UserIdentity(null, List.of(), false);
    }
}
