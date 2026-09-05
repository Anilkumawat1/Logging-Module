package com.anil.logging.security;

import java.util.Optional;

public interface UserIdentityProvider {
    Optional<UserIdentity> getCurrentUser();
}
