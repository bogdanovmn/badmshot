package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.infrastructure.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/screenshots")
@RequiredArgsConstructor
class ScreenshotController {

    private final ScreenshotService screenshotService;
    private final FrameTeaserService frameTeaserService;
    private final FrameRateLimit frameRateLimit;
    private final ImageResizer imageResizer;

    @GetMapping("/teaser")
    List<ScreenshotCard> teaser() {
        return frameTeaserService.cards();
    }

    @GetMapping("/random")
    @PreAuthorize("isAuthenticated()")
    ScreenshotCard random(@CurrentUserId UUID userId) {
        return screenshotService.random(userId);
    }

    @GetMapping("/top")
    @PreAuthorize("isAuthenticated()")
    List<ScreenshotCard> top(@CurrentUserId UUID userId) {
        return screenshotService.top(userId);
    }

    @GetMapping("/{id}/crop/{width}")
    ResponseEntity<byte[]> crop(
        @PathVariable("id") UUID id,
        @PathVariable("width") int width,
        @CurrentUserId UUID userId
    ) {
        if (userId == null && !frameTeaserService.contains(id)) {
            throw new ScreenshotAccessDeniedException("Кадр доступен только после авторизации");
        }
        ScreenshotImage source = screenshotService.image(id);
        ScreenshotImage resized = imageResizer.crop(source, width);
        return ResponseEntity.ok()
            .contentType(mediaType(resized.getFormat()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .body(resized.getData());
    }

    @GetMapping("/next")
    @PreAuthorize("isAuthenticated()")
    ScreenshotCard next(
        @CurrentUserId UUID userId,
        @RequestParam(name = "players", required = false) List<UUID> players,
        @RequestParam(name = "clubs", required = false) List<UUID> clubs,
        @RequestParam(name = "indi", defaultValue = "false") boolean includeIndi
    ) {
        if (!frameRateLimit.allow(userId)) {
            throw new TooManyRequestsException("Слишком часто. Подождите немного и попробуйте ещё раз.");
        }
        return screenshotService.next(filter(players, clubs, includeIndi), userId);
    }

    @PostMapping("/{id}/view")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void view(@PathVariable("id") UUID id, @CurrentUserId UUID userId) {
        screenshotService.requestView(id, userId);
    }

    @GetMapping("/liked")
    @PreAuthorize("isAuthenticated()")
    List<ScreenshotCard> liked(@CurrentUserId UUID userId) {
        return screenshotService.liked(userId);
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("isAuthenticated()")
    void like(@PathVariable("id") UUID id, @CurrentUserId UUID userId) {
        screenshotService.like(id, userId);
    }

    @DeleteMapping("/{id}/like")
    @PreAuthorize("isAuthenticated()")
    void unlike(@PathVariable("id") UUID id, @CurrentUserId UUID userId) {
        screenshotService.unlike(id, userId);
    }

    private ScreenshotFilter filter(List<UUID> players, List<UUID> clubs, boolean includeIndi) {
        return ScreenshotFilter.builder()
            .players(players == null ? List.of() : players)
            .clubs(clubs == null ? List.of() : clubs)
            .includeIndi(includeIndi)
            .build();
    }

    private MediaType mediaType(String format) {
        return MediaType.parseMediaType("image/" + format);
    }
}
