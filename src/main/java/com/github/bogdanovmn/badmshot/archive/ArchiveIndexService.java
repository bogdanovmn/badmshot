package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.job.Job;
import com.github.bogdanovmn.badmshot.job.JobService;
import com.github.bogdanovmn.badmshot.video.ScreenshotCommand;
import com.github.bogdanovmn.badmshot.video.VideoPlayer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ArchiveIndexService {

    private final JobService jobService;
    private final ArchiveIndexTask task;
    private final Path archiveDir;
    private final int defaultCount;
    private final int defaultMarginSeconds;
    private final int defaultReduceFactor;
    private final int defaultQuality;
    private final int defaultThreads;
    private final String defaultPlayer;
    private final String defaultFormat;
    private final String playerPath;

    public ArchiveIndexService(
        JobService jobService,
        ArchiveIndexTask task,
        @Value("${badmshot.archive-dir}") String archiveDir,
        @Value("${badmshot.index.screenshots-count:12}") int defaultCount,
        @Value("${badmshot.index.margin-seconds:30}") int defaultMarginSeconds,
        @Value("${badmshot.index.reduce-factor:2}") int defaultReduceFactor,
        @Value("${badmshot.index.quality:80}") int defaultQuality,
        @Value("${badmshot.index.threads:2}") int defaultThreads,
        @Value("${badmshot.index.player:ffmpeg}") String defaultPlayer,
        @Value("${badmshot.index.format:jpeg}") String defaultFormat,
        @Value("${badmshot.video.player-path:ffmpeg}") String playerPath
    ) {
        this.jobService = jobService;
        this.task = task;
        this.archiveDir = Path.of(archiveDir);
        this.defaultCount = defaultCount;
        this.defaultMarginSeconds = defaultMarginSeconds;
        this.defaultReduceFactor = defaultReduceFactor;
        this.defaultQuality = defaultQuality;
        this.defaultThreads = defaultThreads;
        this.defaultPlayer = defaultPlayer;
        this.defaultFormat = defaultFormat;
        this.playerPath = playerPath;
    }

    public Job start(IndexRequest request) {
        IndexOptions options = IndexOptions.builder()
            .archiveDir(archiveDir)
            .count(value(request.getScreenshotsCount(), defaultCount))
            .marginSeconds(value(request.getMarginSeconds(), defaultMarginSeconds))
            .reduceFactor(value(request.getReduceFactor(), defaultReduceFactor))
            .quality(value(request.getQuality(), defaultQuality))
            .threads(value(request.getThreads(), defaultThreads))
            .player(VideoPlayer.valueOf(value(request.getPlayer(), defaultPlayer).toUpperCase()))
            .playerPath(playerPath)
            .format(ScreenshotCommand.OutputFormat.valueOf(value(request.getFormat(), defaultFormat).toLowerCase()))
            .build();

        return jobService.start(JobService.TYPE_INDEX, params(options), context -> task.run(context, options));
    }

    private Map<String, Object> params(IndexOptions options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("archiveDir", options.getArchiveDir().toString());
        params.put("count", options.getCount());
        params.put("marginSeconds", options.getMarginSeconds());
        params.put("reduceFactor", options.getReduceFactor());
        params.put("quality", options.getQuality());
        params.put("threads", options.getThreads());
        params.put("player", options.getPlayer().name());
        params.put("format", options.getFormat().name());
        return params;
    }

    private <T> T value(T requestValue, T defaultValue) {
        return requestValue == null ? defaultValue : requestValue;
    }

    private String value(String requestValue, String defaultValue) {
        return requestValue == null || requestValue.isBlank() ? defaultValue : requestValue;
    }
}
