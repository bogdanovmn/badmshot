package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PlayerRenameMatch {
    UUID id;
    String relPath;
    String club;
    List<String> players;
    List<Integer> selectedPositions;
}
