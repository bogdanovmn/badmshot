package com.github.bogdanovmn.badmshot.content;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FrameTeaserService {

    private static final UUID ANONYMOUS_USER = new UUID(0, 0);
    private static final String TEASER_KEY = "teaser";

    private final ScreenshotRepository screenshotRepository;
    private final LoadingCache<String, List<ScreenshotCard>> teaser;

    FrameTeaserService(
        ScreenshotRepository screenshotRepository,
        @Value("${badmshot.teaser.cache-days:1}") long cacheDays
    ) {
        this.screenshotRepository = screenshotRepository;
        this.teaser = Caffeine.newBuilder()
            .refreshAfterWrite(Duration.ofDays(Math.max(1, cacheDays)))
            .maximumSize(1)
            .build(key -> screenshotRepository.teaserCards(ANONYMOUS_USER));
    }

    List<ScreenshotCard> cards() {
        return teaser.get(TEASER_KEY);
    }

    boolean contains(UUID id) {
        return cards().stream().anyMatch(card -> card.getId().equals(id));
    }

    /**
     * Reloads the teaser after archive changes. Doing it here keeps the first user request fast.
     */
    public void invalidate() {
        try {
            teaser.put(TEASER_KEY, screenshotRepository.teaserCards(ANONYMOUS_USER));
        } catch (RuntimeException e) {
            teaser.invalidateAll();
            log.warn("Can't refresh teaser cache: {}", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void warmUp() {
        invalidate();
    }
}
