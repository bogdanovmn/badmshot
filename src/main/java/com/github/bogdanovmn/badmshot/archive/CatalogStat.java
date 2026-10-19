package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class CatalogStat {
    UUID id;
    String name;
    int matches;
    long sizeMbytes;
    long durationSeconds;
}
