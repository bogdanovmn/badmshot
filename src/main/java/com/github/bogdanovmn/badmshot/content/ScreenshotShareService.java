package com.github.bogdanovmn.badmshot.content;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ScreenshotShareService {

    private static final UUID ANONYMOUS_USER = new UUID(0, 0);

    private final ScreenshotShareRepository shareRepository;
    private final ScreenshotRepository screenshotRepository;
    private final ScreenshotService screenshotService;

    @Value("${badmshot.shares.visitor-salt:badmshot}")
    private String visitorSalt;

    @Value("${badmshot.shares.view-cooldown-seconds:300}")
    private int viewCooldownSeconds;

    @Transactional
    UUID create(UUID screenshotId, UUID userId) {
        if (screenshotRepository.findById(screenshotId).isEmpty()) {
            throw new NoSuchElementException("Кадр не найден: " + screenshotId);
        }
        return shareRepository.createOrGet(screenshotId, userId);
    }

    @Transactional(readOnly = true)
    List<SharedFrame> myShares(UUID userId, int limit) {
        return screenshotRepository.sharedFrames(userId, limit);
    }

    @Transactional(readOnly = true)
    PublicSharedFrame publicFrame(UUID token) {
        return PublicSharedFrame.from(
            screenshotRepository.sharedFrame(token, ANONYMOUS_USER)
                .orElseThrow(() -> new NoSuchElementException("Ссылка не найдена"))
        );
    }

    @Transactional(readOnly = true)
    ScreenshotImage image(UUID token) {
        UUID screenshotId = shareRepository.screenshotId(token)
            .orElseThrow(() -> new NoSuchElementException("Ссылка не найдена"));
        return screenshotService.image(screenshotId);
    }

    @Transactional
    void registerView(UUID token, UUID userId, String clientAddress) {
        if (shareRepository.screenshotId(token).isEmpty()) {
            throw new NoSuchElementException("Ссылка не найдена");
        }
        shareRepository.registerView(token, visitorKey(userId, clientAddress), viewCooldownSeconds);
    }

    private String visitorKey(UUID userId, String clientAddress) {
        if (userId != null) {
            return "user:" + userId;
        }
        String address = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress.trim();
        return "ip:" + sha256(address + "|" + visitorSalt);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
