package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
class PlayerRelationStat {
    UUID id;
    String label;
    long matches;
}
