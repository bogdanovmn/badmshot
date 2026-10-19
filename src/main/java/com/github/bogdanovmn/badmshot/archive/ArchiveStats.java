package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ArchiveStats {
    int matches;
    long sizeMbytes;
    long durationSeconds;
    List<ArchiveYearStat> years;
    List<CatalogStat> clubs;
    List<CatalogStat> players;
}
