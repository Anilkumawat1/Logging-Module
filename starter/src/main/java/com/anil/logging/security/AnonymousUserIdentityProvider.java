package com.anil.logging.security;

import java.util.Optional;

public class AnonymousUserIdentityProvider implements UserIdentityProvider {
    @Override
    public Optional<UserIdentity> getCurrentUser() {
        return Optional.of(UserIdentity.anonymous());
    }
}
