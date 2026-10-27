package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.infrastructure.security.CurrentUserId;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ScreenshotCommentService commentService;
    private final ScreenshotShareService shareService;
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
    @JsonView(ScreenshotCard.PublicView.class)
    List<ScreenshotCard> top() {
        return screenshotService.top();
    }

    @GetMapping("/commented")
    @PreAuthorize("isAuthenticated()")
    List<ScreenshotCard> commented(@CurrentUserId UUID userId) {
        return screenshotService.commented(userId);
    }

    @GetMapping("/mine/comments")
    @PreAuthorize("isAuthenticated()")
    List<ScreenshotCard> myComments(@CurrentUserId UUID userId) {
        return screenshotService.myCommented(userId);
    }

    @GetMapping("/mine/shares")
    @PreAuthorize("isAuthenticated()")
    List<SharedFrame> myShares(@CurrentUserId UUID userId) {
        return shareService.myShares(userId, screenshotService.profileLimit());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ScreenshotCard card(@PathVariable("id") UUID id, @CurrentUserId UUID userId) {
        return screenshotService.card(id, userId);
    }

    @PostMapping("/{id}/share")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    ScreenshotShareToken share(@PathVariable("id") UUID id, @CurrentUserId UUID userId) {
        return ScreenshotShareToken.builder()
            .token(shareService.create(id, userId))
            .build();
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    List<ScreenshotComment> comments(@PathVariable("id") UUID id) {
        return commentService.list(id);
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    ScreenshotComment addComment(
        @PathVariable("id") UUID id,
        @Valid @RequestBody ScreenshotCommentRequest request,
        @CurrentUserId UUID userId
    ) {
        return commentService.add(id, userId, request.getText());
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

    @GetMapping("/rate-limit")
    @PreAuthorize("isAuthenticated()")
    FrameRateLimitStatus rateLimit(@CurrentUserId UUID userId) {
        return frameRateLimit.status(userId);
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
