package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.video.ScreenshotCommand;
import com.github.bogdanovmn.badmshot.video.VideoPlayer;
import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

@Value
@Builder
class IndexOptions {
    IndexMode mode;
    Path archiveDir;
    int count;
    int marginSeconds;
    int reduceFactor;
    int quality;
    int threads;
    VideoPlayer player;
    String playerPath;
    ScreenshotCommand.OutputFormat format;
}
