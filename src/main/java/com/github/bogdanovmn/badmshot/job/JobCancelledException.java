package com.github.bogdanovmn.badmshot.job;

public class JobCancelledException extends RuntimeException {
    public JobCancelledException() {
        super("Задача отменена");
    }
}
