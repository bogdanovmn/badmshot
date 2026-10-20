package com.github.bogdanovmn.badmshot.catalog;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
class Club {
    UUID id;
    String name;
    String displayName;

    public String getDisplayLabel() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }
}
