package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
class SessionManifest {
    int formatVersion;
    SessionManifestSession session;
    List<SessionManifestMatch> matches;
}
