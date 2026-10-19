package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.video.ScreenshotCommand;
import com.github.bogdanovmn.badmshot.video.VideoPlayer;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScreenshotOptions {
    int count;
    int marginSeconds;
    int reduceFactor;
    int quality;
    VideoPlayer player;
    String playerPath;
    ScreenshotCommand.OutputFormat format;
}
