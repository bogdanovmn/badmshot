package com.github.bogdanovmn.badmshot.user;

import com.github.bogdanovmn.badmshot.infrastructure.security.JwtBasedUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final Set<UUID> registered = ConcurrentHashMap.newKeySet();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtBasedUserDetails user
            && registered.add(user.getUserId()))
        {
            try {
                userService.register(user.getUserId(), user.getUsername());
            } catch (RuntimeException e) {
                registered.remove(user.getUserId());
                log.warn("Can't register user {}: {}", user.getUserId(), e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
