package com.github.bogdanovmn.badmshot.user;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
class UserFrameFilterRequest {
    List<UUID> playerIds;
    List<UUID> clubIds;
    boolean includeIndi;

    List<UUID> playersOrEmpty() {
        return playerIds == null ? List.of() : playerIds;
    }

    List<UUID> clubsOrEmpty() {
        return clubIds == null ? List.of() : clubIds;
    }
}
