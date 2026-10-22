package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
class SessionManifestScreenshot {
    UUID id;
    int timecodeSeconds;
    String format;
    int width;
    int height;
}
