package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
class ScreenshotComment {
    UUID id;
    UUID screenshotId;
    String author;
    String text;
    Instant createdAt;
}
