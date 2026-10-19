package com.github.bogdanovmn.badmshot.job;

@FunctionalInterface
public interface JobTask {
    void run(JobContext context) throws Exception;
}
