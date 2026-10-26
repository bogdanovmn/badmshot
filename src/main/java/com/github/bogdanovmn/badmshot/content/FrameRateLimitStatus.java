package com.github.bogdanovmn.badmshot.content;

public record FrameRateLimitStatus(
    int minSecondsBetweenFrames,
    int freeFrames,
    int freeFramesLeft,
    int retryAfterSeconds
) {
}
