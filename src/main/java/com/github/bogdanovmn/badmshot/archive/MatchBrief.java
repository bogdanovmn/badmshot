package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
class MatchBrief {
    UUID id;
    String relPath;
    Integer year;
    Integer month;
    LocalDate matchDate;
    UUID clubId;
    String clubName;
    boolean tournament;
    Integer durationSeconds;
    int sizeMbytes;
    String validationError;
    List<String> players;

    public String getFileName() {
        int separator = relPath == null ? -1 : relPath.lastIndexOf('/');
        return separator < 0 ? relPath : relPath.substring(separator + 1);
    }

    public boolean isValid() {
        return validationError == null;
    }
}
