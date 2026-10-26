package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
class ArchiveSessionFile {
    UUID id;
    String relPath;
    List<String> players;
    List<UUID> playerIds;
    int sizeMbytes;
    Integer durationSeconds;
    int screenshots;
    String validationError;
    String comment;
}
