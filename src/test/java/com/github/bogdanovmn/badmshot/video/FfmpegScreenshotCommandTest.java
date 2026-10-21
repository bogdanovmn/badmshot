package com.github.bogdanovmn.badmshot.video;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegScreenshotCommandTest {

    private final Path video = Path.of("video.mp4");

    @Test
    void jpegCommandUsesQualityAndChromaSubsampling() {
        String command = command(ScreenshotCommand.OutputFormat.jpeg, size(1920, 1080), 80);

        assertTrue(command.contains("-q:v 8"));
        assertTrue(command.contains("-pix_fmt yuvj420p"));
        assertTrue(command.contains("-vf \"scale=1920:1080\""));
        assertTrue(command.contains("shot.jpg"));
    }

    @Test
    void webpCommandUsesLibwebpQuality() {
        String command = command(ScreenshotCommand.OutputFormat.webp, size(1920, 1080), 75);

        assertTrue(command.contains("-c:v libwebp"));
        assertTrue(command.contains("-quality 75"));
        assertTrue(command.contains("-preset photo"));
        assertTrue(command.contains("-compression_level 4"));
        assertTrue(command.contains("shot.webp"));
    }

    @Test
    void pngCommandUsesCompressionLevel() {
        String command = command(ScreenshotCommand.OutputFormat.png, null, 100);

        assertTrue(command.contains("-compression_level 9"));
    }

    @Test
    void avifCommandUsesCrf() {
        String command = command(ScreenshotCommand.OutputFormat.avif, null, 80);

        assertTrue(command.contains("-c:v libaom-av1"));
        assertTrue(command.contains("-crf 13"));
    }

    @Test
    void skipsQualityAndScaleWhenNotSet() {
        String command = command(ScreenshotCommand.OutputFormat.webp, null, 0);

        assertFalse(command.contains("-quality"));
        assertFalse(command.contains("-vf"));
    }

    @Test
    void mapsJpegQualityToQScale() {
        assertTrue(FfmpegScreenshotCommand.jpegQScale(100) == 2);
        assertTrue(FfmpegScreenshotCommand.jpegQScale(1) == 31);
    }

    @Test
    void mapsAvifQualityToCrf() {
        assertTrue(FfmpegScreenshotCommand.avifCrf(100) == 0);
        assertTrue(FfmpegScreenshotCommand.avifCrf(1) == 62);
    }

    private String command(ScreenshotCommand.OutputFormat format, ScreenshotSize size, int quality) {
        return FfmpegScreenshotCommand.builder()
            .ffmpegPath(Path.of("ffmpeg"))
            .outputDir(Path.of("out"))
            .format(format)
            .size(size)
            .quality(quality)
            .build()
            .buildCommand(video, 42, "shot");
    }

    private ScreenshotSize size(int width, int height) {
        return ScreenshotSize.builder().width(width).height(height).build();
    }
}
