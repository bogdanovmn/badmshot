package com.github.bogdanovmn.badmshot.content;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
class LastServedFrames {

    private final Cache<UUID, ServedFrame> frames = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(1))
        .maximumSize(10_000)
        .build();

    ServedFrame get(UUID userId) {
        return userId == null ? null : frames.getIfPresent(userId);
    }

    void put(UUID userId, ScreenshotCard card) {
        if (userId == null) {
            return;
        }
        frames.put(userId, ServedFrame.builder()
            .screenshotId(card.getId())
            .matchId(card.getMatchId())
            .clubId(card.getClubId())
            .build()
        );
    }
}
