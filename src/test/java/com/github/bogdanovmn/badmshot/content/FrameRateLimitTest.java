package com.github.bogdanovmn.badmshot.content;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameRateLimitTest {

    @Test
    void allowsFreeFramesThenDenies() {
        FrameRateLimit limit = new FrameRateLimit(100, 2);
        UUID user = UUID.randomUUID();

        assertTrue(limit.allow(user));
        assertTrue(limit.allow(user));
        assertFalse(limit.allow(user));
    }

    @Test
    void limitsArePerUser() {
        FrameRateLimit limit = new FrameRateLimit(100, 1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(limit.allow(first));
        assertFalse(limit.allow(first));
        assertTrue(limit.allow(second));
    }

    @Test
    void freeFramesCannotBeLessThanOne() {
        FrameRateLimit limit = new FrameRateLimit(100, 0);
        UUID user = UUID.randomUUID();

        assertTrue(limit.allow(user));
        assertFalse(limit.allow(user));
    }
}
