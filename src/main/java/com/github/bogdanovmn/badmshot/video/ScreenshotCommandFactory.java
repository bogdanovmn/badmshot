package com.github.bogdanovmn.badmshot.video;

import lombok.Builder;

import java.nio.file.Path;

@Builder
public class ScreenshotCommandFactory {
    private final Path playerPath;
    private final Path outputDir;
    private final ScreenshotCommand.OutputFormat format;
    private final int reduceFactor;
    private final int quality;

    public ScreenshotCommand of(VideoPlayer player) {
        switch (player) {
            case VLC:
                return new VlcScreenshotCommand(playerPath, outputDir, format);
            case FFMPEG:
                return FfmpegScreenshotCommand.builder()
                    .ffmpegPath(playerPath)
                    .outputDir(outputDir)
                    .format(format)
                    .reduceFactor(reduceFactor)
                    .quality(quality)
                    .build();
            default:
                throw new IllegalArgumentException("Unsupported video player type: " + player);
        }
    }
}
