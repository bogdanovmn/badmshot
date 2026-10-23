package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder(toBuilder = true)
class ArchiveSession {
    UUID id;
    ArchiveType type;
    String name;
    LocalDate matchDate;
    Integer year;
    Integer month;
    UUID clubId;
    String club;
    String clubName;
    boolean tournament;
    int matches;
    long sizeMbytes;
    long durationSeconds;
    int invalid;
    boolean exportable;
    List<ArchiveSessionFile> files;
}
