package com.github.bogdanovmn.badmshot.job;

public enum JobStatus {
    RUNNING,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    FAILED,
    STUCK;

    public boolean isActive() {
        return this == RUNNING || this == CANCELLING;
    }
}
