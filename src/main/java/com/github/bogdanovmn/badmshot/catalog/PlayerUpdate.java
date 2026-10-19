package com.github.bogdanovmn.badmshot.catalog;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlayerUpdate {
    String displayName;
    String tournamentAlias;
}
