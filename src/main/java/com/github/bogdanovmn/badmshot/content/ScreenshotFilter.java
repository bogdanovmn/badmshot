package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
class ScreenshotFilter {
    List<UUID> players;
    List<UUID> clubs;
    boolean includeIndi;
    /**
     * true — кадр должен содержать всех выбранных игроков,
     * false — достаточно любого из выбранных игроков.
     */
    boolean together;

    public static ScreenshotFilter empty() {
        return ScreenshotFilter.builder()
            .players(List.of())
            .clubs(List.of())
            .includeIndi(false)
            .together(false)
            .build();
    }

    public List<UUID> playersOrEmpty() {
        return players == null ? List.of() : players;
    }

    public List<UUID> clubsOrEmpty() {
        return clubs == null ? List.of() : clubs;
    }
}
