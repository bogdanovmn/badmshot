package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
class ArchivePath {
    String relPath;
    Integer year;
    Integer month;
    LocalDate matchDate;
    String club;
    boolean tournament;
    List<String> players;
    String validationError;

    public boolean valid() {
        return validationError == null;
    }
}
