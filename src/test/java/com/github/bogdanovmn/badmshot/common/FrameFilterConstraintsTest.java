package com.github.bogdanovmn.badmshot.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameFilterConstraintsTest {

    private final FrameFilterConstraints constraints = new FrameFilterConstraints(4);

    @Test
    void allowsUpToFourPlayersTogether() {
        assertDoesNotThrow(() -> constraints.validate(players(4), true));
    }

    @Test
    void rejectsMoreThanFourPlayersTogether() {
        assertThrows(
            IllegalArgumentException.class,
            () -> constraints.validate(players(5), true)
        );
    }

    @Test
    void doesNotLimitPlayersInAnyMode() {
        assertDoesNotThrow(() -> constraints.validate(players(10), false));
    }

    @Test
    void exposesConfiguredLimit() {
        assertEquals(4, constraints.maxTogetherPlayers());
    }

    private List<UUID> players(int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> UUID.randomUUID())
            .toList();
    }
}
