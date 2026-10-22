package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ScreenshotExport {
    UUID id;
    UUID matchId;
    int timecodeSeconds;
    String imageFormat;
    int width;
    int height;
    byte[] data;
}
