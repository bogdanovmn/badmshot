package com.github.bogdanovmn.badmshot.job;

import java.util.UUID;

public interface JobContext {

    UUID jobId();

    void phase(JobPhase phase);

    boolean cancelled();

    void totalFiles(int totalFiles);

    void processed(int processedFiles, int screenshotsCreated);

    void heartbeat();
}
