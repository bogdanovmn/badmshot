package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class IndexRequest {
    IndexMode mode;
    Integer screenshotsCount;
    Integer marginSeconds;
    Integer reduceFactor;
    Integer quality;
    Integer threads;
    String player;
    String format;
}
