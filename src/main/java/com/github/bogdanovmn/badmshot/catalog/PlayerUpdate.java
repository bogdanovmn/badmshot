package com.github.bogdanovmn.badmshot.catalog;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class PlayerUpdate {
    String displayName;
    String tournamentAlias;
}
