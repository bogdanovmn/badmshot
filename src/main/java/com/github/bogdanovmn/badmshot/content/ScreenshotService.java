package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ScreenshotService {

    private static final UUID ANONYMOUS_USER = new UUID(0, 0);

    private final ScreenshotRepository screenshotRepository;
    private final PlayerRepository playerRepository;
    private final LastServedFrames lastServedFrames;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${badmshot.top-frames-limit:100}")
    private int topFramesLimit;

    @Value("${badmshot.profile-frames-limit:200}")
    private int profileFramesLimit;

    @Transactional(readOnly = true)
    ScreenshotCard random(UUID userId) {
        return next(ScreenshotFilter.empty(), userId);
    }

    @Transactional(readOnly = true)
    ScreenshotCard next(ScreenshotFilter filter, UUID userId) {
        ScreenshotFilter normalized = normalize(filter);
        if (!filter.playersOrEmpty().isEmpty() && normalized.getPlayers().isEmpty()) {
            throw new NoSuchElementException("Кадры не найдены");
        }
        ServedFrame last = lastServedFrames.get(userId);
        Optional<ScreenshotCard> card = screenshotRepository.randomCard(
            normalized, userIdOrAnonymous(userId), last
        );
        if (card.isEmpty() && last != null) {
            card = screenshotRepository.randomCard(
                normalized,
                userIdOrAnonymous(userId),
                ServedFrame.builder().screenshotId(last.getScreenshotId()).build()
            );
        }
        if (card.isEmpty() && last != null) {
            card = screenshotRepository.randomCard(normalized, userIdOrAnonymous(userId), null);
        }
        ScreenshotCard result = card.orElseThrow(() -> new NoSuchElementException("Кадры не найдены"));
        lastServedFrames.put(userId, result);
        return result;
    }

    void requestView(UUID id, UUID userId) {
        eventPublisher.publishEvent(ScreenshotViewEvent.builder()
            .screenshotId(id)
            .userId(userId)
            .build()
        );
    }

    private ScreenshotFilter normalize(ScreenshotFilter filter) {
        return ScreenshotFilter.builder()
            .players(playerRepository.canonicalIds(filter.playersOrEmpty()))
            .clubs(filter.clubsOrEmpty())
            .includeIndi(filter.isIncludeIndi())
            .build();
    }

    @Transactional(readOnly = true)
    List<ScreenshotCard> top() {
        return screenshotRepository.topCards(ANONYMOUS_USER, topFramesLimit);
    }

    @Transactional(readOnly = true)
    List<ScreenshotCard> liked(UUID userId) {
        return screenshotRepository.likedCards(userId, topFramesLimit);
    }

    @Transactional(readOnly = true)
    List<ScreenshotCard> commented(UUID userId) {
        return screenshotRepository.commentedCards(userIdOrAnonymous(userId), topFramesLimit);
    }

    @Transactional(readOnly = true)
    List<ScreenshotCard> myCommented(UUID userId) {
        return screenshotRepository.userCommentedCards(userId, profileFramesLimit);
    }

    int topLimit() {
        return topFramesLimit;
    }

    int profileLimit() {
        return profileFramesLimit;
    }

    @Transactional(readOnly = true)
    ScreenshotCard card(UUID id, UUID userId) {
        return screenshotRepository.card(id, userIdOrAnonymous(userId))
            .orElseThrow(() -> new NoSuchElementException("Кадр не найден: " + id));
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
