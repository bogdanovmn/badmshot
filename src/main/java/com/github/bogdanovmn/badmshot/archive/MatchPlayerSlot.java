package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
class MatchPlayerSlot {
    UUID matchId;
    ArchiveType type;
    String relPath;
    int position;
    int displayPosition;
}
