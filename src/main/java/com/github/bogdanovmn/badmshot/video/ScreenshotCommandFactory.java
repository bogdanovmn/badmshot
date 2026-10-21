package com.github.bogdanovmn.badmshot.video;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Builder
@Slf4j
public class ScreenshotCommandFactory {
    private final Path playerPath;
    private final Path outputDir;
    private final ScreenshotCommand.OutputFormat format;
    private final ScreenshotSize size;
    private final int quality;

    public ScreenshotCommand of(VideoPlayer player) {
        ScreenshotSize frameSize = size == null ? ScreenshotSize.original() : size;
        switch (player) {
            case VLC:
                requireVlcFormat();
                if (quality > 0) {
                    log.warn("VLC doesn't support screenshot quality, the value {} is ignored", quality);
                }
                return new VlcScreenshotCommand(playerPath, outputDir, format, frameSize);
            case FFMPEG:
                return FfmpegScreenshotCommand.builder()
                    .ffmpegPath(playerPath)
                    .outputDir(outputDir)
                    .format(format)
                    .size(frameSize)
                    .quality(quality)
                    .build();
            default:
                throw new IllegalArgumentException("Unsupported video player type: " + player);
        }
    }

    private void requireVlcFormat() {
        if (format != ScreenshotCommand.OutputFormat.jpeg && format != ScreenshotCommand.OutputFormat.png) {
            throw new IllegalArgumentException("VLC supports only jpeg and png screenshot format, requested: " + format);
        }
    }
}
