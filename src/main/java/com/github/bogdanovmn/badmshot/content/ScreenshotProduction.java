package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScreenshotProduction {
    int created;
    long bytesTotal;
    long bytesMax;
}
