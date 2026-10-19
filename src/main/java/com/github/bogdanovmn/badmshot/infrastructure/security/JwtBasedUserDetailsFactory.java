package com.github.bogdanovmn.badmshot.infrastructure.security;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class JwtBasedUserDetailsFactory {

    @Value("${security.roles.application-id-prefix}")
    private String roleApplicationPrefix;

    public UserDetails fromJwtClaims(Claims claims) {
        List<? extends GrantedAuthority> roles = ((List<String>) claims.get("roles")).stream()
            .map(
                r -> r.replaceFirst(
                    "^(any|%s):".formatted(roleApplicationPrefix),
                    ""
                )
            )
            .map(SimpleGrantedAuthority::new)
            .toList();

        return new JwtBasedUserDetails(
            UUID.fromString(
                claims.get("userId", String.class)
            ),
            claims.get("userName", String.class),
            roles
        );
    }
}
