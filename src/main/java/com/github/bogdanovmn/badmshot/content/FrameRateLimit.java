package com.github.bogdanovmn.badmshot.content;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

@Component
class FrameRateLimit {

    private final int minSecondsBetweenFrames;
    private final int freeFrames;
    private final Cache<UUID, Deque<Instant>> servedFrames = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(1))
        .maximumSize(10_000)
        .build();

    FrameRateLimit(
        @Value("${badmshot.rate-limit.min-seconds-between-frames:3}") int minSecondsBetweenFrames,
        @Value("${badmshot.rate-limit.free-frames:3}") int freeFrames
    ) {
        this.minSecondsBetweenFrames = minSecondsBetweenFrames;
        this.freeFrames = Math.max(1, freeFrames);
    }

    boolean allow(UUID userId) {
        boolean[] allowed = {false};
        servedFrames.asMap().compute(userId, (key, times) -> {
            Deque<Instant> frames = times == null ? new ArrayDeque<>() : times;
            Instant now = Instant.now();
            if (frames.size() < freeFrames) {
                frames.addLast(now);
                allowed[0] = true;
            } else {
                Instant last = frames.peekLast();
                if (last == null || !now.isBefore(last.plusSeconds(minSecondsBetweenFrames))) {
                    frames.addLast(now);
                    while (frames.size() > freeFrames) {
                        frames.pollFirst();
                    }
                    allowed[0] = true;
                }
            }
            return frames;
        });
        return allowed[0];
    }
}
