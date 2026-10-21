package com.github.bogdanovmn.badmshot.video;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class VideoProbe {
    private final String ffprobePath;

    public VideoProbe(String ffprobePath) {
        this.ffprobePath = ffprobePath;
    }

    public Optional<VideoMeta> probe(Path videoFile) {
        String command = String.format(
            "%s -v error -select_streams v:0 -show_entries stream=width,height:format=duration -of default=noprint_wrappers=1 \"%s\"",
            ffprobePath,
            videoFile
        );
        return SystemCommand.builder()
            .command(command)
            .maxExecutionTimeInSeconds(60)
            .captureOutput(true)
            .build()
            .output()
            .flatMap(this::parse);
    }

    private Optional<VideoMeta> parse(String output) {
        Map<String, String> values = new HashMap<>();
        for (String line : output.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
        try {
            int duration = (int) Math.floor(Double.parseDouble(values.getOrDefault("duration", "0")));
            int width = Integer.parseInt(values.getOrDefault("width", "0"));
            int height = Integer.parseInt(values.getOrDefault("height", "0"));
            if (duration <= 0 || width <= 0 || height <= 0) {
                log.warn("Incomplete video meta: {}", values);
                return Optional.empty();
            }
            return Optional.of(
                VideoMeta.builder()
                    .durationSeconds(duration)
                    .width(width)
                    .height(height)
                    .build()
            );
        } catch (NumberFormatException e) {
            log.warn("Can't parse video meta: {}", values);
            return Optional.empty();
        }
    }
}
