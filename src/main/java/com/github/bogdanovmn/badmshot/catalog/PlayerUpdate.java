package com.github.bogdanovmn.badmshot.catalog;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
class PlayerUpdate {
    String displayName;
    UUID aliasPlayerId;
}
