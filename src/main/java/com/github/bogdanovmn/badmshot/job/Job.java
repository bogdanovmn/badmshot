package com.github.bogdanovmn.badmshot.job;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class Job {
    UUID id;
    String type;
    JobStatus status;
    JobPhase phase;
    String params;
    int totalFiles;
    int processedFiles;
    int screenshotsCreated;
    long screenshotBytesTotal;
    long screenshotBytesMax;
    String fileName;
    String sessionName;
    boolean cancelRequested;
    String error;
    Instant startedAt;
    Instant lastActivityAt;
    Instant finishedAt;
}
