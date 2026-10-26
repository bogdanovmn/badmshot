package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class Screenshot {
    UUID id;
    UUID matchId;
    int timecodeSeconds;
    String imageFormat;
    int width;
    int height;
    long sizeBytes;
    Instant createdAt;
}
