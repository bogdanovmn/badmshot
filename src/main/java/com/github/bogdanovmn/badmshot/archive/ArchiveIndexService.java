package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.job.Job;
import com.github.bogdanovmn.badmshot.job.JobService;
import com.github.bogdanovmn.badmshot.video.ScreenshotCommand;
import com.github.bogdanovmn.badmshot.video.VideoPlayer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
class ArchiveIndexService {

    private static final Set<ScreenshotCommand.OutputFormat> ALLOWED_FORMATS =
        EnumSet.of(ScreenshotCommand.OutputFormat.jpeg);

    private final JobService jobService;
    private final ArchiveIndexTask task;
    private final Path archiveDir;
    private final int defaultCount;
    private final int defaultMarginSeconds;
    private final int defaultScalePercent;
    private final int defaultQuality;
    private final int defaultThreads;
    private final String defaultPlayer;
    private final String defaultFormat;
    private final String playerPath;

    @Value("${badmshot.index.enabled:true}")
    private boolean indexEnabled;

    public ArchiveIndexService(
        JobService jobService,
        ArchiveIndexTask task,
        @Value("${badmshot.archive-dir}") String archiveDir,
        @Value("${badmshot.index.screenshots-count}") int defaultCount,
        @Value("${badmshot.index.margin-seconds}") int defaultMarginSeconds,
        @Value("${badmshot.index.scale-percent}") int defaultScalePercent,
        @Value("${badmshot.index.quality}") int defaultQuality,
        @Value("${badmshot.index.threads}") int defaultThreads,
        @Value("${badmshot.index.player}") String defaultPlayer,
        @Value("${badmshot.index.format}") String defaultFormat,
        @Value("${badmshot.video.player-path}") String playerPath
    ) {
        this.jobService = jobService;
        this.task = task;
        this.archiveDir = Path.of(archiveDir);
        this.defaultCount = defaultCount;
        this.defaultMarginSeconds = defaultMarginSeconds;
        this.defaultScalePercent = defaultScalePercent;
        this.defaultQuality = defaultQuality;
        this.defaultThreads = defaultThreads;
        this.defaultPlayer = defaultPlayer;
        this.defaultFormat = defaultFormat;
        this.playerPath = playerPath;
    }

    public Job start(IndexRequest request) {
        if (!indexEnabled) {
            throw new IllegalStateException("Индексация отключена");
        }
        IndexOptions options = IndexOptions.builder()
            .mode(value(request.getMode(), IndexMode.META))
            .archiveDir(archiveDir)
            .count(value(request.getScreenshotsCount(), defaultCount))
            .marginSeconds(value(request.getMarginSeconds(), defaultMarginSeconds))
            .scalePercent(scalePercent(value(request.getScalePercent(), defaultScalePercent)))
            .quality(value(request.getQuality(), defaultQuality))
            .threads(value(request.getThreads(), defaultThreads))
            .player(VideoPlayer.valueOf(value(request.getPlayer(), defaultPlayer).toUpperCase(Locale.ROOT)))
            .playerPath(playerPath)
            .format(format(value(request.getFormat(), defaultFormat)))
            .build();

        requireSupportedPlayerFormat(options);

        return jobService.start(JobService.TYPE_INDEX, params(options), context -> task.run(context, options));
    }

    IndexDefaults defaults() {
        return IndexDefaults.builder()
            .screenshotsCount(defaultCount)
            .marginSeconds(defaultMarginSeconds)
            .scalePercent(defaultScalePercent)
            .quality(defaultQuality)
            .threads(defaultThreads)
            .player(defaultPlayer.toLowerCase(Locale.ROOT))
            .format(defaultFormat.toLowerCase(Locale.ROOT))
            .build();
    }

    private void requireSupportedPlayerFormat(IndexOptions options) {
        if (options.getPlayer() == VideoPlayer.VLC && options.getFormat() != ScreenshotCommand.OutputFormat.jpeg
            && options.getFormat() != ScreenshotCommand.OutputFormat.png) {
            throw new IllegalArgumentException(
                "VLC supports only jpeg and png screenshot format, requested: " + options.getFormat()
            );
        }
    }

    private Map<String, Object> params(IndexOptions options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", options.getMode().name());
        params.put("archiveDir", options.getArchiveDir().toString());
        params.put("count", options.getCount());
        params.put("marginSeconds", options.getMarginSeconds());
        params.put("scalePercent", options.getScalePercent());
        params.put("quality", options.getQuality());
        params.put("threads", options.getThreads());
        params.put("player", options.getPlayer().name());
        params.put("format", options.getFormat().name());
        return params;
    }

    private ScreenshotCommand.OutputFormat format(String value) {
        ScreenshotCommand.OutputFormat format;
        try {
            format = ScreenshotCommand.OutputFormat.valueOf(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown screenshot format: " + value);
        }
        if (!ALLOWED_FORMATS.contains(format)) {
            throw new IllegalArgumentException(
                "Unsupported screenshot format: " + format + ". Allowed: " + ALLOWED_FORMATS
            );
        }
        return format;
    }

    private int scalePercent(int value) {
        return Math.max(1, Math.min(100, value));
    }

    private <T> T value(T requestValue, T defaultValue) {
        return requestValue == null ? defaultValue : requestValue;
    }

    private String value(String requestValue, String defaultValue) {
        return requestValue == null || requestValue.isBlank() ? defaultValue : requestValue;
    }
}
