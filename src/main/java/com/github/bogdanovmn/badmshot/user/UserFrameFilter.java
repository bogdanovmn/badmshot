package com.github.bogdanovmn.badmshot.user;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
class UserFrameFilter {
    List<UUID> playerIds;
    List<UUID> clubIds;
    boolean includeIndi;
    Instant updatedAt;

    static UserFrameFilter empty() {
        return UserFrameFilter.builder()
            .playerIds(List.of())
            .clubIds(List.of())
            .includeIndi(false)
            .build();
    }

    List<UUID> playersOrEmpty() {
        return playerIds == null ? List.of() : playerIds;
    }

    List<UUID> clubsOrEmpty() {
        return clubIds == null ? List.of() : clubIds;
    }
}
