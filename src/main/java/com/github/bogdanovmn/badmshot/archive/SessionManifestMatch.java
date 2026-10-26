package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
class SessionManifestMatch {
    String relPath;
    Integer durationSeconds;
    int sizeMbytes;
    List<String> players;
    String comment;
    List<SessionManifestScreenshot> screenshots;
}
