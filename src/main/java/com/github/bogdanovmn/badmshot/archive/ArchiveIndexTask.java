package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.ClubRepository;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import com.github.bogdanovmn.badmshot.content.ScreenshotOptions;
import com.github.bogdanovmn.badmshot.content.ScreenshotProducer;
import com.github.bogdanovmn.badmshot.content.ScreenshotProduction;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
class ArchiveIndexTask {

    private final ArchivePathParser pathParser;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final ArchiveSessionRepository sessionRepository;
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

        int processed = 0;
        for (Path file : files) {
            if (context.cancelled()) {
                return;
            }
            indexMeta(context, options.getArchiveDir(), file);
            context.processed(++processed, 0, 0, 0);
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
        ArchiveType type = parsed.getType() == null ? ArchiveType.GAMES : parsed.getType();
        int sizeMbytes = sizeMbytes(file);

        UUID clubId = null;
        List<UUID> playerIds = List.of();
        if (type == ArchiveType.GAMES && parsed.getClub() != null) {
            clubId = clubRepository.upsert(parsed.getClub());
        }
        if (parsed.valid()) {
            playerIds = parsed.getPlayers().stream()
                .map(playerRepository::upsert)
                .collect(Collectors.toList());
        }

        UUID sessionId = parsed.getSessionName() == null
            ? null
            : sessionRepository.upsert(
                parsed.getSessionName(),
                parsed.sessionDate(),
                clubId,
                parsed.isTournament(),
                type
            );

        UUID matchId = matchRepository.upsertMeta(
            parsed.getRelPath(),
            sessionId,
            sizeMbytes,
            parsed.getValidationError(),
            type
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
        AtomicLong screenshotBytesTotal = new AtomicLong();
        AtomicLong screenshotBytesMax = new AtomicLong();
        ExecutorService workers = Executors.newFixedThreadPool(Math.max(1, options.getThreads()));
        try {
            for (Match match : matches) {
                workers.submit(() -> processMatch(
                    context,
                    options,
                    match,
                    workDir,
                    processed,
                    screenshots,
                    screenshotBytesTotal,
                    screenshotBytesMax
                ));
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
        AtomicInteger screenshots,
        AtomicLong screenshotBytesTotal,
        AtomicLong screenshotBytesMax
    ) {
        if (context.cancelled()) {
            return;
        }
        Path video = options.getArchiveDir().resolve(match.getRelPath());
        Optional<VideoMeta> probed = videoProbe.probe(video);
        if (probed.isEmpty()) {
            log.warn("Can't probe video: {}", video);
            reportProcessed(context, processed, screenshots, screenshotBytesTotal, screenshotBytesMax);
            return;
        }
        VideoMeta meta = probed.get();
        if (match.getDurationSeconds() == null || match.getDurationSeconds() != meta.getDurationSeconds()) {
            matchRepository.updateDuration(match.getId(), meta.getDurationSeconds());
        }
        context.heartbeat();

        int existing = screenshotRepository.countByMatch(match.getId());
        if (existing >= options.getCount()) {
            reportProcessed(context, processed, screenshots, screenshotBytesTotal, screenshotBytesMax);
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
        ScreenshotProduction production = screenshotProducer.produce(
            workDir,
            match.getId(),
            video,
            meta,
            screenshotOptions
        );
        screenshots.addAndGet(production.getCreated());
        screenshotBytesTotal.addAndGet(production.getBytesTotal());
        screenshotBytesMax.accumulateAndGet(production.getBytesMax(), Math::max);
        reportProcessed(context, processed, screenshots, screenshotBytesTotal, screenshotBytesMax);
    }

    private void reportProcessed(
        JobContext context,
        AtomicInteger processed,
        AtomicInteger screenshots,
        AtomicLong screenshotBytesTotal,
        AtomicLong screenshotBytesMax
    ) {
        context.processed(
            processed.incrementAndGet(),
            screenshots.get(),
            screenshotBytesTotal.get(),
            screenshotBytesMax.get()
        );
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
