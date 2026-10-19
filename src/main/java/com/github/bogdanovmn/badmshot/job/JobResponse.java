package com.github.bogdanovmn.badmshot.job;

import lombok.Builder;
import lombok.Value;

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
    boolean cancelRequested;
    String error;
    Instant startedAt;
    Instant lastActivityAt;
    Instant finishedAt;

    public static JobResponse of(Job job) {
        return JobResponse.builder()
            .id(job.getId())
            .type(job.getType())
            .status(job.getStatus().name())
            .phase(job.getPhase() == null ? null : job.getPhase().name())
            .totalFiles(job.getTotalFiles())
            .processedFiles(job.getProcessedFiles())
            .screenshotsCreated(job.getScreenshotsCreated())
            .cancelRequested(job.isCancelRequested())
            .error(job.getError())
            .startedAt(job.getStartedAt())
            .lastActivityAt(job.getLastActivityAt())
            .finishedAt(job.getFinishedAt())
            .build();
    }
}
