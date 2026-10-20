package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.infrastructure.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/screenshots")
@RequiredArgsConstructor
class ScreenshotController {

    private final ScreenshotService screenshotService;
    private final FrameRateLimit frameRateLimit;
    private final ImageResizer imageResizer;

    @GetMapping("/random")
    ScreenshotCard random(
        @RequestParam(name = "players", required = false) List<UUID> players,
        @RequestParam(name = "clubs", required = false) List<UUID> clubs,
        @RequestParam(name = "excludeMatchId", required = false) UUID excludeMatchId,
        @RequestParam(name = "excludeClubId", required = false) UUID excludeClubId
    ) {
        return screenshotService.random(filter(players, clubs), null, excludeMatchId, excludeClubId);
    }

    @GetMapping("/top")
    List<ScreenshotCard> top(@CurrentUserId UUID userId) {
        return screenshotService.top(userId);
    }

    @GetMapping("/{id}/crop/{width}")
    ResponseEntity<byte[]> crop(@PathVariable("id") UUID id, @PathVariable("width") int width) {
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
        @RequestParam(name = "excludeMatchId", required = false) UUID excludeMatchId,
        @RequestParam(name = "excludeClubId", required = false) UUID excludeClubId
    ) {
        if (!frameRateLimit.allow(userId)) {
            throw new TooManyRequestsException("Слишком часто. Подождите несколько секунд.");
        }
        return screenshotService.random(filter(players, clubs), userId, excludeMatchId, excludeClubId);
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

    private ScreenshotFilter filter(List<UUID> players, List<UUID> clubs) {
        return ScreenshotFilter.builder()
            .players(players == null ? List.of() : players)
            .clubs(clubs == null ? List.of() : clubs)
            .build();
    }

    private MediaType mediaType(String format) {
        return MediaType.parseMediaType("image/" + format);
    }
}
