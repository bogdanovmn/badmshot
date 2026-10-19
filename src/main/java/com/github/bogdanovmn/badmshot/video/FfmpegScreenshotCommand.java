package com.github.bogdanovmn.badmshot.video;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Builder
@Slf4j
class FfmpegScreenshotCommand implements ScreenshotCommand {
    private final Path ffmpegPath;
    private final Path outputDir;
    private final OutputFormat format;
    private final int reduceFactor;
    private final int quality;

    @Override
    public boolean execute(Path videoFile, long timestamp, String outputName) {
        String outputFileName = String.format("%s.%s", outputName, format.ffmpegExtension());
        Path output = outputDir.resolve(outputFileName);

        StringBuilder command = new StringBuilder()
            .append(quote(ffmpegPath.toString()))
            .append(" -y -ss ").append(timestamp)
            .append(" -i ").append(quote(videoFile.toString()))
            .append(" -frames:v 1")
            .append(" -update 1");

        if (reduceFactor > 1) {
            command.append(" -vf \"scale=iw/").append(reduceFactor)
                .append(":ih/").append(reduceFactor).append("\"");
        }
        if (quality > 0 && (format == OutputFormat.jpeg || format == OutputFormat.webp)) {
            int qScale = Math.max(2, Math.round((100 - quality) * 31f / 100f));
            command.append(" -q:v ").append(qScale);
        }
        command.append(" ").append(quote(output.toString()));

        return SystemCommand.builder()
            .command(command.toString())
            .maxExecutionTimeInSeconds(60)
            .build()
            .execute();
    }

    private String quote(String value) {
        return "\"" + value + "\"";
    }
}
