package com.github.bogdanovmn.badmshot.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;


@RequiredArgsConstructor
@Slf4j
class VlcScreenshotCommand implements ScreenshotCommand {
    private final Path vlcPath;
    private final Path outputDir;
    private final OutputFormat format;

    @Override
    public boolean execute(Path videoFile, long timestamp, String outputName) {
        String vlcFormat = format == OutputFormat.jpeg ? "jpg" : "png";
        String currentCommand = String.format(
            "%sq %s -I rc --rate=1 --video-filter=scene --vout=dummy --start-time=%d --stop-time=%d --scene-format=%s --scene-ratio=24 --scene-prefix=%s --scene-path=%s vlc://quit",
                vlcPath,
                videoFile,
                timestamp,
                timestamp + 1,
                vlcFormat,
                outputName,
                outputDir
        );
        try {
            boolean commandIsOk = SystemCommand.builder()
                .command(currentCommand)
                .maxExecutionTimeInSeconds(60)
            .build().execute();
            if (commandIsOk) {
                File fileToDelete = outputDir.resolve(
                    String.format("%s00025.%s", outputName, vlcFormat)
                ).toFile();
                if (fileToDelete.exists() && !fileToDelete.delete()) {
                    log.warn("Can't delete file: {}", fileToDelete.getAbsolutePath());
                }
            }
            return commandIsOk;
        } catch (Exception e) {
            log.error("Run command error: {}\nCommand: {}", e.getMessage(), currentCommand);
            return false;
        }
    }

}
