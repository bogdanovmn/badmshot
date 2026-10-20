package com.github.bogdanovmn.badmshot.job;

enum JobStatus {
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
