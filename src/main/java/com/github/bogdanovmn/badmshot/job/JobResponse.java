package com.github.bogdanovmn.badmshot.job;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class JobResponse {
    UUID id;
    String type;
    String status;
    String phase;
    int totalFiles;
    int processedFiles;
    int screenshotsCreated;
    long screenshotBytesTotal;
    long screenshotBytesAverage;
    long screenshotBytesMax;
    String fileName;
    String sessionName;
    boolean cancelRequested;
    String error;
    Instant startedAt;
    Instant lastActivityAt;
    Instant finishedAt;
    long durationSeconds;
    long lastActivitySecondsAgo;

    public static JobResponse of(Job job) {
        return JobResponse.builder()
            .id(job.getId())
            .type(job.getType())
            .status(job.getStatus().name())
            .phase(job.getPhase() == null ? null : job.getPhase().name())
            .totalFiles(job.getTotalFiles())
            .processedFiles(job.getProcessedFiles())
            .screenshotsCreated(job.getScreenshotsCreated())
            .screenshotBytesTotal(job.getScreenshotBytesTotal())
            .screenshotBytesAverage(averageBytes(job))
            .screenshotBytesMax(job.getScreenshotBytesMax())
            .fileName(job.getFileName())
            .sessionName(job.getSessionName())
            .cancelRequested(job.isCancelRequested())
            .error(job.getError())
            .startedAt(job.getStartedAt())
            .lastActivityAt(job.getLastActivityAt())
            .finishedAt(job.getFinishedAt())
            .durationSeconds(durationSeconds(job))
            .lastActivitySecondsAgo(secondsAgo(job.getLastActivityAt()))
            .build();
    }

    private static long averageBytes(Job job) {
        return job.getScreenshotsCreated() == 0
            ? 0
            : job.getScreenshotBytesTotal() / job.getScreenshotsCreated();
    }

    private static long durationSeconds(Job job) {
        Instant end = job.getFinishedAt() == null ? Instant.now() : job.getFinishedAt();
        return Math.max(0, Duration.between(job.getStartedAt(), end).getSeconds());
    }

    private static long secondsAgo(Instant instant) {
        return Math.max(0, Duration.between(instant, Instant.now()).getSeconds());
    }
}
