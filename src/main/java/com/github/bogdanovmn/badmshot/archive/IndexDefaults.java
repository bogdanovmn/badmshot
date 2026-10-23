package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class IndexDefaults {
    int screenshotsCount;
    int marginSeconds;
    int scalePercent;
    int quality;
    int threads;
    String player;
    String format;
}
