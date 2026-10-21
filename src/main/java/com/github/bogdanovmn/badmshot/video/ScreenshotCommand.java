package com.github.bogdanovmn.badmshot.video;

import java.nio.file.Path;

public interface ScreenshotCommand {

    enum OutputFormat {
        jpeg, png, webp, avif;

        public String fileExtension() {
            return this == jpeg ? "jpg" : this.name();
        }
    }

    boolean execute(Path videoFile, long timestamp, String outputName);
}
