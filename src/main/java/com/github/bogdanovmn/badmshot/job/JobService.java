package com.github.bogdanovmn.badmshot.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
public class JobService {

    public static final String TYPE_INDEX = "index";

    private final JobRepository jobRepository;
    private final ExecutorService jobExecutor;
    private final ObjectMapper objectMapper;
    private final long staleTimeoutSeconds;

    public JobService(
        JobRepository jobRepository,
        ExecutorService jobExecutor,
        ObjectMapper objectMapper,
        @Value("${badmshot.job.stale-timeout-seconds:60}") long staleTimeoutSeconds
    ) {
        this.jobRepository = jobRepository;
        this.jobExecutor = jobExecutor;
        this.objectMapper = objectMapper;
        this.staleTimeoutSeconds = staleTimeoutSeconds;
    }

    public Job start(String type, Map<String, Object> params, JobTask task) {
        jobRepository.markStaleStuck(type, staleTimeoutSeconds);
        jobRepository.findActive(type).ifPresent(active -> {
            throw new JobConflictException("Задача уже выполняется: " + active.getId());
        });

        UUID jobId = jobRepository.create(type, toJson(params), JobPhase.META);
        Context context = new Context(jobId);
        jobExecutor.submit(() -> execute(jobId, task, context));
        return jobRepository.findById(jobId).orElseThrow();
    }

    public Optional<Job> current(String type) {
        return jobRepository.findLatest(type);
    }

    public Optional<Job> byId(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    public void cancel(String type) {
        Job job = jobRepository.findActive(type)
            .orElseThrow(() -> new NoSuchElementException("Активная задача не найдена"));
        jobRepository.requestCancel(job.getId());
    }

    private void execute(UUID jobId, JobTask task, JobContext context) {
        try {
            task.run(context);
            if (jobRepository.isCancelRequested(jobId)) {
                jobRepository.finish(jobId, JobStatus.CANCELLED, null);
            } else {
                jobRepository.finish(jobId, JobStatus.COMPLETED, null);
            }
        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            jobRepository.finish(jobId, JobStatus.FAILED, exceptionMessage(e));
        }
    }

    private String toJson(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Can't serialize job params", e);
        }
    }

    private String exceptionMessage(Throwable e) {
        return Optional.ofNullable(e.getMessage())
            .orElse(e.getClass().getSimpleName());
    }

    private class Context implements JobContext {
        private final UUID jobId;

        private Context(UUID jobId) {
            this.jobId = jobId;
        }

        @Override
        public UUID jobId() {
            return jobId;
        }

        @Override
        public void phase(JobPhase phase) {
            jobRepository.setPhase(jobId, phase);
        }

        @Override
        public boolean cancelled() {
            return jobRepository.isCancelRequested(jobId);
        }

        @Override
        public void totalFiles(int totalFiles) {
            jobRepository.setTotalFiles(jobId, totalFiles);
        }

        @Override
        public void processed(int processedFiles, int screenshotsCreated) {
            jobRepository.updateProgress(jobId, processedFiles, screenshotsCreated);
        }

        @Override
        public void heartbeat() {
            jobRepository.touch(jobId);
        }
    }
}
