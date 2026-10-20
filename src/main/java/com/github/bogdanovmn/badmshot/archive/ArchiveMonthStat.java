package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class ArchiveMonthStat {
    int month;
    int matches;
    long sizeMbytes;
    long durationSeconds;
}
