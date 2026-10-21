package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.video.ScreenshotSize;
import com.github.bogdanovmn.badmshot.video.VideoMeta;

final class ScreenshotScale {

    private ScreenshotScale() {
    }

    static ScreenshotSize of(VideoMeta meta, int scalePercent) {
        if (scalePercent >= 100) {
            return ScreenshotSize.original();
        }
        int percent = Math.max(1, Math.min(100, scalePercent));
        return ScreenshotSize.builder()
            .width(even(meta.getWidth() * percent / 100.0))
            .height(even(meta.getHeight() * percent / 100.0))
            .build();
    }

    private static int even(double value) {
        return Math.max(2, (int) Math.round(value) & ~1);
    }
}
