package com.github.bogdanovmn.badmshot.content;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class ScreenshotViewListener {

    private final ScreenshotRepository screenshotRepository;
    private final int cooldownSeconds;

    ScreenshotViewListener(
        ScreenshotRepository screenshotRepository,
        @Value("${badmshot.views.cooldown-seconds:300}") int cooldownSeconds
    ) {
        this.screenshotRepository = screenshotRepository;
        this.cooldownSeconds = cooldownSeconds;
    }

    @Async
    @EventListener
    public void on(ScreenshotViewEvent event) {
        try {
            screenshotRepository.registerView(
                event.getScreenshotId(),
                event.getUserId(),
                cooldownSeconds
            );
        } catch (RuntimeException e) {
            log.warn(
                "Can't register view for screenshot {} by user {}: {}",
                event.getScreenshotId(), event.getUserId(), e.getMessage()
            );
        }
    }
}
