package com.github.bogdanovmn.badmshot.content;

import com.github.bogdanovmn.badmshot.video.ScreenshotCommand;
import com.github.bogdanovmn.badmshot.video.ScreenshotCommandFactory;
import com.github.bogdanovmn.badmshot.video.ScreenshotSize;
import com.github.bogdanovmn.badmshot.video.VideoMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScreenshotProducer {

    private static final List<String> IMAGE_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp", "avif");

    private final ScreenshotRepository screenshotRepository;

    public ScreenshotProduction produce(Path workDir, UUID matchId, Path videoFile, VideoMeta meta, ScreenshotOptions options) {
        ScreenshotSize size = ScreenshotScale.of(meta, options.getScalePercent());
        ScreenshotCommand command = ScreenshotCommandFactory.builder()
            .playerPath(Path.of(options.getPlayerPath()))
            .outputDir(workDir)
            .format(options.getFormat())
            .size(size)
            .quality(options.getQuality())
            .build()
            .of(options.getPlayer());

        int width = size.isOriginal() ? meta.getWidth() : size.getWidth();
        int height = size.isOriginal() ? meta.getHeight() : size.getHeight();

        int created = 0;
        long bytesTotal = 0;
        long bytesMax = 0;
        for (int timecode : timecodes(meta, options)) {
            String outputName = UUID.randomUUID().toString();
            if (!command.execute(videoFile, timecode, outputName)) {
                log.warn("Can't make screenshot at {} sec for {}", timecode, videoFile);
                continue;
            }
            Path result = producedFile(workDir, outputName);
            if (result == null) {
                log.warn("Screenshot file not found for {} at {} sec", videoFile, timecode);
                continue;
            }
            try {
                byte[] image = Files.readAllBytes(result);
                screenshotRepository.insert(
                    matchId,
                    timecode,
                    image,
                    options.getFormat().name(),
                    width,
                    height
                );
                created++;
                bytesTotal += image.length;
                bytesMax = Math.max(bytesMax, image.length);
            } catch (IOException e) {
                log.warn("Can't read screenshot {}: {}", result, e.getMessage());
            } finally {
                deleteQuietly(result);
            }
        }
        return ScreenshotProduction.builder()
            .created(created)
            .bytesTotal(bytesTotal)
            .bytesMax(bytesMax)
            .build();
    }

    private List<Integer> timecodes(VideoMeta meta, ScreenshotOptions options) {
        int duration = meta.getDurationSeconds();
        int count = Math.max(1, options.getCount());
        int margin = Math.max(0, options.getMarginSeconds());
        List<Integer> result = new ArrayList<>();
        if (count == 1) {
            result.add(duration / 2);
            return result;
        }
        double step;
        double start;
        if (duration > 2 * margin) {
            start = margin;
            step = (duration - 2.0 * margin) / (count - 1);
        } else {
            start = 0;
            step = Math.max(duration - 1, 1) / (double) (count - 1);
        }
        for (int i = 0; i < count; i++) {
            result.add((int) Math.round(start + i * step));
        }
        return result;
    }

    private Path producedFile(Path workDir, String outputName) {
        try (Stream<Path> files = Files.list(workDir)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    if (!name.startsWith(outputName)) {
                        return false;
                    }
                    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
                    return IMAGE_EXTENSIONS.contains(extension);
                })
                .max(Comparator.comparingLong(path -> path.toFile().length()))
                .orElse(null);
        } catch (IOException e) {
            log.warn("Can't list work dir {}: {}", workDir, e.getMessage());
            return null;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Can't delete {}: {}", path, e.getMessage());
        }
    }
}
