package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
class PlayerClubStat {
    UUID id;
    String name;
    int matches;
    long sizeMbytes;
    long durationSeconds;
    List<PlayerClubYearStat> years;
}
