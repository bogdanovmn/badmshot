package com.github.bogdanovmn.badmshot.video;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScreenshotSize {
    int width;
    int height;

    public static ScreenshotSize original() {
        return new ScreenshotSize(0, 0);
    }

    public boolean isOriginal() {
        return width <= 0 || height <= 0;
    }
}
