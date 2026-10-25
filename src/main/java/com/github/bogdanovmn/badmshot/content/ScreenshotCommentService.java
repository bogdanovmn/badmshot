package com.github.bogdanovmn.badmshot.content;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ScreenshotCommentService {

    private static final int MAX_TEXT_LENGTH = 1000;

    private final ScreenshotCommentRepository commentRepository;
    private final ScreenshotRepository screenshotRepository;

    @Transactional(readOnly = true)
    List<ScreenshotComment> list(UUID screenshotId) {
        requireScreenshot(screenshotId);
        return commentRepository.byScreenshot(screenshotId);
    }

    @Transactional
    ScreenshotComment add(UUID screenshotId, UUID userId, String text) {
        requireScreenshot(screenshotId);
        String normalized = normalize(text);
        return commentRepository.create(screenshotId, userId, normalized);
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Комментарий не может быть пустым");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Комментарий не может быть длиннее " + MAX_TEXT_LENGTH + " символов");
        }
        return trimmed;
    }

    private void requireScreenshot(UUID screenshotId) {
        if (screenshotRepository.findById(screenshotId).isEmpty()) {
            throw new NoSuchElementException("Кадр не найден: " + screenshotId);
        }
    }
}
