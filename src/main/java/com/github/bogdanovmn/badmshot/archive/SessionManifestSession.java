package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
class SessionManifestSession {
    ArchiveType type;
    String name;
    LocalDate matchDate;
    String club;
    boolean tournament;
    String group;
}
