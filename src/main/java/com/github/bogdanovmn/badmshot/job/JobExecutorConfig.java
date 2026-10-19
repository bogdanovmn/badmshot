package com.github.bogdanovmn.badmshot.job;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
class JobExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService jobExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "job-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
