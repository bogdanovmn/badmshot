package com.github.bogdanovmn.badmshot.video;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VlcScreenshotCommandTest {

    private final Path video = Path.of("video.mp4");

    @Test
    void passesSceneSizeToVlc() {
        String command = command(ScreenshotCommand.OutputFormat.jpeg, size(1280, 720));

        assertTrue(command.contains("--scene-format=jpg"));
        assertTrue(command.contains("--scene-width=1280"));
        assertTrue(command.contains("--scene-height=720"));
        assertTrue(command.endsWith(" vlc://quit"));
    }

    @Test
    void keepsOriginalSizeWhenSizeIsNotSet() {
        String command = command(ScreenshotCommand.OutputFormat.png, ScreenshotSize.original());

        assertFalse(command.contains("--scene-width"));
        assertFalse(command.contains("--scene-height"));
    }

    @Test
    void rejectsWebpFormat() {
        ScreenshotCommandFactory factory = ScreenshotCommandFactory.builder()
            .playerPath(Path.of("vlc"))
            .outputDir(Path.of("out"))
            .format(ScreenshotCommand.OutputFormat.webp)
            .size(ScreenshotSize.original())
            .quality(0)
            .build();

        assertThrows(IllegalArgumentException.class, () -> factory.of(VideoPlayer.VLC));
    }

    private String command(ScreenshotCommand.OutputFormat format, ScreenshotSize size) {
        return new VlcScreenshotCommand(Path.of("vlc"), Path.of("out"), format, size)
            .buildCommand(video, 42, "shot");
    }

    private ScreenshotSize size(int width, int height) {
        return ScreenshotSize.builder().width(width).height(height).build();
    }
}
