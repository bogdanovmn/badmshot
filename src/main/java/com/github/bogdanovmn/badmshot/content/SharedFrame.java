package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
class SharedFrame {
    UUID token;
    long views;
    Instant createdAt;
    ScreenshotCard card;
}
