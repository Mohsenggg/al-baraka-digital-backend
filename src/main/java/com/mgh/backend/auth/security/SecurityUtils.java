package com.mgh.backend.auth.security;

import com.mgh.backend.auth.security.adapter.UserAuthAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static long requireUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Authentication is required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserAuthAdapter adapter) {
            return adapter.getUserAuth().getId();
        }
        throw new IllegalStateException("Unexpected authentication principal");
    }
}
