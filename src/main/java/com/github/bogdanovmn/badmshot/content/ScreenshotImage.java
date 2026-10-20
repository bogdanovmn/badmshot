package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class ScreenshotImage {
    byte[] data;
    String format;
    int width;
    int height;
}
