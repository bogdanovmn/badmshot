package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
class ArchivePath {
    ArchiveType type;
    String relPath;
    String sessionName;
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

    public LocalDate sessionDate() {
        if (type == ArchiveType.INDI && year != null && month != null) {
            return LocalDate.of(year, month, 1);
        }
        return matchDate;
    }
}
