package com.github.bogdanovmn.badmshot.content;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
class FrameTeaserService {

    private static final UUID ANONYMOUS_USER = new UUID(0, 0);
    private static final String TEASER_KEY = "teaser";

    private final ScreenshotRepository screenshotRepository;
    private final Cache<String, List<ScreenshotCard>> teaser;

    FrameTeaserService(
        ScreenshotRepository screenshotRepository,
        @Value("${badmshot.teaser.cache-days:1}") long cacheDays
    ) {
        this.screenshotRepository = screenshotRepository;
        this.teaser = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(Math.max(1, cacheDays)))
            .maximumSize(1)
            .build();
    }

    @Transactional(readOnly = true)
    List<ScreenshotCard> cards() {
        return teaser.get(TEASER_KEY, key -> screenshotRepository.teaserCards(ANONYMOUS_USER));
    }

    boolean contains(UUID id) {
        return cards().stream().anyMatch(card -> card.getId().equals(id));
    }
}
