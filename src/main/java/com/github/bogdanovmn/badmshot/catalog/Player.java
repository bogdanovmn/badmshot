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
    String tournamentAlias;

    public String getDisplayLabel() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }
}
