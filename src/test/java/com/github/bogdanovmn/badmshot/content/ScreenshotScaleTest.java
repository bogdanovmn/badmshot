package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.video.ScreenshotSize;
import com.github.bogdanovmn.badmshot.video.VideoMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotScaleTest {

    private final VideoMeta meta = VideoMeta.builder()
        .width(3840)
        .height(2160)
        .build();

    @Test
    void keepsOriginalSizeForFullPercent() {
        assertTrue(ScreenshotScale.of(meta, 100).isOriginal());
    }

    @Test
    void scalesByPercent() {
        ScreenshotSize size = ScreenshotScale.of(meta, 50);

        assertEquals(1920, size.getWidth());
        assertEquals(1080, size.getHeight());
    }

    @Test
    void roundsToEvenSizes() {
        VideoMeta odd = VideoMeta.builder().width(1919).height(1079).build();

        ScreenshotSize size = ScreenshotScale.of(odd, 50);

        assertEquals(960, size.getWidth());
        assertEquals(540, size.getHeight());
    }

    @Test
    void clampsPercentToMinimum() {
        ScreenshotSize size = ScreenshotScale.of(meta, 0);

        assertEquals(38, size.getWidth());
        assertEquals(22, size.getHeight());
    }

    @Test
    void treatsPercentAboveHundredAsOriginal() {
        assertTrue(ScreenshotScale.of(meta, 150).isOriginal());
    }
}
