package com.github.bogdanovmn.badmshot.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Общие ограничения фильтра кадров.
 * В режиме «вместе» одновременно можно выбрать не больше четырёх игроков:
 * это отсекает заведомо пустые и слишком тяжёлые комбинации.
 */
@Component
public class FrameFilterConstraints {

    private final int maxTogetherPlayers;

    public FrameFilterConstraints(
        @Value("${badmshot.filters.max-together-players:4}") int maxTogetherPlayers
    ) {
        this.maxTogetherPlayers = Math.max(1, maxTogetherPlayers);
    }

    public void validate(List<UUID> playerIds, boolean together) {
        if (!together || playerIds == null) {
            return;
        }
        if (playerIds.size() > maxTogetherPlayers) {
            throw new IllegalArgumentException(
                "В режиме «вместе» можно выбрать не более " + maxTogetherPlayers + " игроков"
            );
        }
    }

    public int maxTogetherPlayers() {
        return maxTogetherPlayers;
    }
}
