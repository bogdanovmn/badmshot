package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
class ArchiveYearStat {
    int year;
    int matches;
    long sizeMbytes;
    long durationSeconds;
    List<ArchiveMonthStat> months;
}
