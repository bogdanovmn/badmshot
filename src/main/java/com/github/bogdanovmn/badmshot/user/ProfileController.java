package com.github.bogdanovmn.badmshot.user;

import com.github.bogdanovmn.badmshot.infrastructure.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
class ProfileController {

    private final UserFrameFilterService filterService;

    @GetMapping("/filters")
    @PreAuthorize("isAuthenticated()")
    UserFrameFilter filters(@CurrentUserId UUID userId) {
        return filterService.get(userId);
    }

    @PutMapping("/filters")
    @PreAuthorize("isAuthenticated()")
    UserFrameFilter saveFilters(
        @CurrentUserId UUID userId,
        @RequestBody UserFrameFilterRequest request
    ) {
        return filterService.save(userId, request);
    }
}
