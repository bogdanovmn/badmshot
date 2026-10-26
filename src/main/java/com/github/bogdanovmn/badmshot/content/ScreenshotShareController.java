package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.infrastructure.security.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/shares")
@RequiredArgsConstructor
class ScreenshotShareController {

    private final ScreenshotShareService shareService;
    private final ImageResizer imageResizer;

    @GetMapping("/{token}")
    PublicSharedFrame frame(
        @PathVariable("token") UUID token,
        @CurrentUserId UUID userId
    ) {
        return shareService.publicFrame(token, userId);
    }

    @GetMapping("/{token}/crop/{width}")
    ResponseEntity<byte[]> crop(
        @PathVariable("token") UUID token,
        @PathVariable("width") int width
    ) {
        ScreenshotImage source = shareService.image(token);
        ScreenshotImage resized = imageResizer.crop(source, width);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("image/" + resized.getFormat()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
            .body(resized.getData());
    }

    @PostMapping("/{token}/view")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void view(
        @PathVariable("token") UUID token,
        @CurrentUserId UUID userId,
        HttpServletRequest request
    ) {
        shareService.registerView(token, userId, clientAddress(request));
    }

    private String clientAddress(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] addresses = forwarded.split(",");
            return addresses[addresses.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
