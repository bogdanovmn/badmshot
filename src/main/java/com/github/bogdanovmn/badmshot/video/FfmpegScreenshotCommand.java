package com.github.bogdanovmn.badmshot.video;

import lombok.Builder;

import java.nio.file.Path;

@Builder
class FfmpegScreenshotCommand implements ScreenshotCommand {
    private static final int JPEG_MIN_QSCALE = 2;
    private static final int JPEG_MAX_QSCALE = 31;
    private static final int PNG_MAX_COMPRESSION_LEVEL = 9;
    private static final int AVIF_MAX_CRF = 63;

    private final Path ffmpegPath;
    private final Path outputDir;
    private final OutputFormat format;
    private final ScreenshotSize size;
    private final int quality;

    @Override
    public boolean execute(Path videoFile, long timestamp, String outputName) {
        return SystemCommand.builder()
            .command(buildCommand(videoFile, timestamp, outputName))
            .maxExecutionTimeInSeconds(60)
            .build()
            .execute();
    }

    String buildCommand(Path videoFile, long timestamp, String outputName) {
        Path output = outputDir.resolve(String.format("%s.%s", outputName, format.fileExtension()));

        StringBuilder command = new StringBuilder()
            .append(quote(ffmpegPath.toString()))
            .append(" -y -ss ").append(timestamp)
            .append(" -i ").append(quote(videoFile.toString()))
            .append(" -frames:v 1")
            .append(" -update 1");

        appendSize(command);
        appendQuality(command);
        command.append(" ").append(quote(output.toString()));
        return command.toString();
    }

    private void appendSize(StringBuilder command) {
        if (size == null || size.isOriginal()) {
            return;
        }
        command.append(" -vf \"scale=")
            .append(size.getWidth())
            .append(':')
            .append(size.getHeight())
            .append('"');
    }

    private void appendQuality(StringBuilder command) {
        if (quality <= 0) {
            return;
        }
        switch (format) {
            case jpeg -> command
                .append(" -q:v ").append(jpegQScale(quality))
                .append(" -pix_fmt yuvj420p");
            case webp -> command
                .append(" -c:v libwebp")
                .append(" -quality ").append(clamp(quality, 0, 100))
                .append(" -preset photo")
                .append(" -compression_level 4");
            case png -> command
                .append(" -compression_level ").append(pngCompressionLevel(quality));
            case avif -> command
                .append(" -c:v libaom-av1")
                .append(" -crf ").append(avifCrf(quality));
            default -> throw new IllegalArgumentException("Unsupported screenshot format: " + format);
        }
    }

    static int jpegQScale(int quality) {
        int normalized = clamp(quality, 0, 100);
        int qscale = Math.round(JPEG_MIN_QSCALE + (100 - normalized) * (JPEG_MAX_QSCALE - JPEG_MIN_QSCALE) / 100f);
        return clamp(qscale, JPEG_MIN_QSCALE, JPEG_MAX_QSCALE);
    }

    static int pngCompressionLevel(int quality) {
        return clamp(Math.round(clamp(quality, 0, 100) * PNG_MAX_COMPRESSION_LEVEL / 100f), 0, PNG_MAX_COMPRESSION_LEVEL);
    }

    static int avifCrf(int quality) {
        return clamp(Math.round((100 - clamp(quality, 0, 100)) * AVIF_MAX_CRF / 100f), 0, AVIF_MAX_CRF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String quote(String value) {
        return "\"" + value + "\"";
    }
}
