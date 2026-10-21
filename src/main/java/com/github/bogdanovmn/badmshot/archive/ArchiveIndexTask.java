package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.ClubRepository;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import com.github.bogdanovmn.badmshot.content.ScreenshotOptions;
import com.github.bogdanovmn.badmshot.content.ScreenshotProducer;
import com.github.bogdanovmn.badmshot.content.ScreenshotRepository;
import com.github.bogdanovmn.badmshot.job.JobContext;
import com.github.bogdanovmn.badmshot.job.JobPhase;
import com.github.bogdanovmn.badmshot.video.VideoMeta;
import com.github.bogdanovmn.badmshot.video.VideoProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
class ArchiveIndexTask {

    private final ArchivePathParser pathParser;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final ScreenshotRepository screenshotRepository;
    private final ScreenshotProducer screenshotProducer;
    private final VideoProbe videoProbe;

    public void run(JobContext context, IndexOptions options) {
        switch (options.getMode()) {
            case META -> indexMetadata(context, options);
            case SCREENSHOTS -> rewriteScreenshots(context, options);
        }
    }

    private void indexMetadata(JobContext context, IndexOptions options) {
        List<Path> files = scan(options.getArchiveDir());
        context.totalFiles(files.size());

        for (Path file : files) {
            if (context.cancelled()) {
                return;
            }
            indexMeta(context, options.getArchiveDir(), file);
        }
    }

    private void rewriteScreenshots(JobContext context, IndexOptions options) {
        screenshotRepository.deleteAll();
        context.phase(JobPhase.SCREENSHOTS);
        makeScreenshots(context, options);
    }

    private void indexMeta(JobContext context, Path archiveDir, Path file) {
        Path relative = archiveDir.relativize(file);
        ArchivePath parsed = pathParser.parse(relative.toString());
        int sizeMbytes = sizeMbytes(file);

        UUID clubId = null;
        List<UUID> playerIds = List.of();
        if (parsed.valid() && parsed.getClub() != null) {
            clubId = clubRepository.upsert(parsed.getClub());
            playerIds = parsed.getPlayers().stream()
                .map(playerRepository::upsert)
                .collect(Collectors.toList());
        }

        UUID matchId = matchRepository.upsertMeta(
            parsed.getRelPath(),
            parsed.getYear(),
            parsed.getMonth(),
            parsed.getMatchDate(),
            clubId,
            parsed.isTournament(),
            sizeMbytes,
            parsed.getValidationError()
        );
        matchRepository.replacePlayers(matchId, playerIds);
        context.heartbeat();
    }

    private void makeScreenshots(JobContext context, IndexOptions options) {
        List<Match> matches = matchRepository.validWithoutScreenshots(Integer.MAX_VALUE);
        context.totalFiles(matches.size());
        if (matches.isEmpty()) {
            return;
        }

        Path workDir = createWorkDir();
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger screenshots = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(Math.max(1, options.getThreads()));
        try {
            for (Match match : matches) {
                workers.submit(() -> processMatch(context, options, match, workDir, processed, screenshots));
            }
        } finally {
            workers.shutdown();
            try {
                workers.awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            deleteWorkDir(workDir);
        }
    }

    private void processMatch(
        JobContext context,
        IndexOptions options,
        Match match,
        Path workDir,
        AtomicInteger processed,
        AtomicInteger screenshots
    ) {
        if (context.cancelled()) {
            return;
        }
        Path video = options.getArchiveDir().resolve(match.getRelPath());
        Optional<VideoMeta> probed = videoProbe.probe(video);
        if (probed.isEmpty()) {
            log.warn("Can't probe video: {}", video);
            reportProcessed(context, processed, screenshots);
            return;
        }
        VideoMeta meta = probed.get();
        if (match.getDurationSeconds() == null || match.getDurationSeconds() != meta.getDurationSeconds()) {
            matchRepository.updateDuration(match.getId(), meta.getDurationSeconds());
        }
        context.heartbeat();

        int existing = screenshotRepository.countByMatch(match.getId());
        if (existing >= options.getCount()) {
            reportProcessed(context, processed, screenshots);
            return;
        }
        if (existing > 0) {
            screenshotRepository.deleteByMatch(match.getId());
        }

        ScreenshotOptions screenshotOptions = ScreenshotOptions.builder()
            .count(options.getCount())
            .marginSeconds(options.getMarginSeconds())
            .scalePercent(options.getScalePercent())
            .quality(options.getQuality())
            .player(options.getPlayer())
            .playerPath(options.getPlayerPath())
            .format(options.getFormat())
            .build();
        int created = screenshotProducer.produce(workDir, match.getId(), video, meta, screenshotOptions);
        screenshots.addAndGet(created);
        reportProcessed(context, processed, screenshots);
    }

    private void reportProcessed(JobContext context, AtomicInteger processed, AtomicInteger screenshots) {
        context.processed(processed.incrementAndGet(), screenshots.get());
    }

    private List<Path> scan(Path archiveDir) {
        if (!Files.isDirectory(archiveDir)) {
            throw new IllegalStateException("Каталог архива не найден: " + archiveDir);
        }
        try (Stream<Path> walk = Files.walk(archiveDir)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".mp4"))
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка обхода архива: " + e.getMessage(), e);
        }
    }

    private int sizeMbytes(Path file) {
        try {
            return (int) Math.max(1, Files.size(file) / (1024 * 1024));
        } catch (IOException e) {
            return 0;
        }
    }

    private Path createWorkDir() {
        try {
            return Files.createTempDirectory("badmshot-shots");
        } catch (IOException e) {
            throw new IllegalStateException("Can't create temp dir: " + e.getMessage(), e);
        }
    }

    private void deleteWorkDir(Path workDir) {
        try (Stream<Path> walk = Files.walk(workDir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Can't delete {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Can't clean work dir {}: {}", workDir, e.getMessage());
        }
    }
}
