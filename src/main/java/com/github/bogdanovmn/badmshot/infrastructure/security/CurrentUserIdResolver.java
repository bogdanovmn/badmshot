package com.github.bogdanovmn.badmshot.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("currentUserIdResolver")
class CurrentUserIdResolver {
    public UUID resolve(Object principal) {
        if (principal instanceof JwtBasedUserDetails user) {
            return user.getUserId();
        }
        return null;
    }
}
