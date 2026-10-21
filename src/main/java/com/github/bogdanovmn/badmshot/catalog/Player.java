package com.github.bogdanovmn.badmshot.catalog;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class Player {
    UUID id;
    String name;
    String displayName;
    UUID aliasOfPlayerId;
    PlayerAlias alias;

    public String getDisplayLabel() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }

    public boolean isAlias() {
        return aliasOfPlayerId != null;
    }
}
