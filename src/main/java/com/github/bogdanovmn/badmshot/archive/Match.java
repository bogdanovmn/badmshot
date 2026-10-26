package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
class Match {
    UUID id;
    UUID sessionId;
    ArchiveType type;
    String relPath;
    Integer year;
    Integer month;
    LocalDate matchDate;
    UUID clubId;
    String clubName;
    boolean tournament;
    String group;
    String comment;
    Integer durationSeconds;
    int sizeMbytes;
    String validationError;
    Instant indexedAt;

    public boolean isValid() {
        return validationError == null;
    }
}
