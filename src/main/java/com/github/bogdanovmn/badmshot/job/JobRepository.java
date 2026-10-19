package com.github.bogdanovmn.badmshot.job;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JobRepository {

    private static final RowMapper<Job> JOB_ROW_MAPPER = (rs, rowNum) -> Job.builder()
        .id(UUID.fromString(rs.getString("id")))
        .type(rs.getString("type"))
        .status(JobStatus.valueOf(rs.getString("status")))
        .phase(rs.getString("phase") == null ? null : JobPhase.valueOf(rs.getString("phase")))
        .params(rs.getString("params"))
        .totalFiles(rs.getInt("total_files"))
        .processedFiles(rs.getInt("processed_files"))
        .screenshotsCreated(rs.getInt("screenshots_created"))
        .cancelRequested(rs.getBoolean("cancel_requested"))
        .error(rs.getString("error"))
        .startedAt(rs.getTimestamp("started_at").toInstant())
        .lastActivityAt(rs.getTimestamp("last_activity_at").toInstant())
        .finishedAt(rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant())
        .build();

    private final NamedParameterJdbcTemplate jdbc;

    UUID create(String type, String params, JobPhase phase) {
        return jdbc.queryForObject("""
                INSERT INTO job (type, status, phase, params)
                VALUES (:type, :status, :phase, :params::jsonb)
                RETURNING id
                """,
            new MapSqlParameterSource()
                .addValue("type", type)
                .addValue("status", JobStatus.RUNNING.name())
                .addValue("phase", phase.name())
                .addValue("params", params),
            UUID.class
        );
    }

    Optional<Job> findActive(String type) {
        return jdbc.query("""
                SELECT *
                FROM job
                WHERE type = :type AND status IN ('RUNNING', 'CANCELLING')
                ORDER BY started_at DESC
                LIMIT 1
                """,
            Map.of("type", type),
            JOB_ROW_MAPPER
        ).stream().findFirst();
    }

    Optional<Job> findLatest(String type) {
        return jdbc.query("""
                SELECT *
                FROM job
                WHERE type = :type
                ORDER BY started_at DESC
                LIMIT 1
                """,
            Map.of("type", type),
            JOB_ROW_MAPPER
        ).stream().findFirst();
    }

    Optional<Job> findById(UUID jobId) {
        return jdbc.query("""
                SELECT *
                FROM job
                WHERE id = :jobId
                """,
            Map.of("jobId", jobId),
            JOB_ROW_MAPPER
        ).stream().findFirst();
    }

    void setPhase(UUID jobId, JobPhase phase) {
        jdbc.update("""
                UPDATE job
                SET phase = :phase, last_activity_at = now()
                WHERE id = :jobId
                """,
            Map.of("jobId", jobId, "phase", phase.name())
        );
    }

    void setTotalFiles(UUID jobId, int totalFiles) {
        jdbc.update("""
                UPDATE job
                SET total_files = :totalFiles, last_activity_at = now()
                WHERE id = :jobId
                """,
            Map.of("jobId", jobId, "totalFiles", totalFiles)
        );
    }

    void updateProgress(UUID jobId, int processedFiles, int screenshotsCreated) {
        jdbc.update("""
                UPDATE job
                SET processed_files = :processedFiles,
                    screenshots_created = :screenshotsCreated,
                    last_activity_at = now()
                WHERE id = :jobId
                """,
            Map.of(
                "jobId", jobId,
                "processedFiles", processedFiles,
                "screenshotsCreated", screenshotsCreated
            )
        );
    }

    void touch(UUID jobId) {
        jdbc.update("""
                UPDATE job
                SET last_activity_at = now()
                WHERE id = :jobId
                """,
            Map.of("jobId", jobId)
        );
    }

    void requestCancel(UUID jobId) {
        jdbc.update("""
                UPDATE job
                SET cancel_requested = true,
                    status = :cancelling,
                    last_activity_at = now()
                WHERE id = :jobId AND status = :running
                """,
            Map.of(
                "jobId", jobId,
                "cancelling", JobStatus.CANCELLING.name(),
                "running", JobStatus.RUNNING.name()
            )
        );
    }

    boolean isCancelRequested(UUID jobId) {
        var result = jdbc.queryForList("""
                SELECT 1 FROM job WHERE id = :jobId AND cancel_requested = true
                """,
            Map.of("jobId", jobId)
        );
        return !result.isEmpty();
    }

    void finish(UUID jobId, JobStatus status, String error) {
        jdbc.update("""
                UPDATE job
                SET status = :status,
                    phase = :phase,
                    error = :error,
                    finished_at = now(),
                    last_activity_at = now()
                WHERE id = :jobId
                """,
            new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("status", status.name())
                .addValue("phase", JobPhase.DONE.name())
                .addValue("error", error)
        );
    }

    void markStaleStuck(String type, long timeoutSeconds) {
        jdbc.update("""
                UPDATE job
                SET status = :status,
                    error = :error,
                    finished_at = now()
                WHERE type = :type
                  AND status IN ('RUNNING', 'CANCELLING')
                  AND last_activity_at < now() - make_interval(secs => :timeout)
                """,
            Map.of(
                "type", type,
                "status", JobStatus.STUCK.name(),
                "error", "Обнаружено зависание: нет активности более заданного таймаута",
                "timeout", timeoutSeconds
            )
        );
    }
}
