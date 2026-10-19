package com.github.bogdanovmn.badmshot.video;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VideoMeta {
    int durationSeconds;
    int width;
    int height;
}
