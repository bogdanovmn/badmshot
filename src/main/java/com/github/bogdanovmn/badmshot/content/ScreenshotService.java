package com.github.bogdanovmn.badmshot.content;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ScreenshotService {

    private static final UUID ANONYMOUS_USER = new UUID(0, 0);

    private final ScreenshotRepository screenshotRepository;

    @Value("${badmshot.top-frames-limit:100}")
    private int topFramesLimit;

    @Transactional(readOnly = true)
    ScreenshotCard random(ScreenshotFilter filter, UUID userId, UUID excludeMatchId, UUID excludeClubId) {
        return screenshotRepository.randomCard(filter, userIdOrAnonymous(userId), excludeMatchId, excludeClubId)
            .orElseThrow(() -> new NoSuchElementException("Кадры не найдены"));
    }

    @Transactional(readOnly = true)
    List<ScreenshotCard> top(UUID userId) {
        return screenshotRepository.topCards(userIdOrAnonymous(userId), topFramesLimit);
    }

    @Transactional(readOnly = true)
    List<ScreenshotCard> liked(UUID userId) {
        return screenshotRepository.likedCards(userId, topFramesLimit);
    }

    @Transactional(readOnly = true)
    ScreenshotImage image(UUID id) {
        return screenshotRepository.image(id)
            .orElseThrow(() -> new NoSuchElementException("Кадр не найден: " + id));
    }

    @Transactional
    void like(UUID id, UUID userId) {
        requireExists(id);
        screenshotRepository.like(id, userId);
    }

    @Transactional
    void unlike(UUID id, UUID userId) {
        requireExists(id);
        screenshotRepository.unlike(id, userId);
    }

    private void requireExists(UUID id) {
        if (screenshotRepository.findById(id).isEmpty()) {
            throw new NoSuchElementException("Кадр не найден: " + id);
        }
    }

    private UUID userIdOrAnonymous(UUID userId) {
        return userId == null ? ANONYMOUS_USER : userId;
    }
}
