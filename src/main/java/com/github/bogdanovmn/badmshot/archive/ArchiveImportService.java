package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.job.Job;
import com.github.bogdanovmn.badmshot.job.JobPhase;
import com.github.bogdanovmn.badmshot.job.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class ArchiveImportService {

    private final JobService jobService;
    private final ArchiveImportTask importTask;
    private final ArchiveImportReader reader;
    private final ArchiveSessionRepository sessionRepository;

    @Value("${badmshot.import.enabled:true}")
    private boolean importEnabled;

    Job start(MultipartFile file) {
        if (!importEnabled) {
            throw new IllegalStateException("Импорт отключён");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран");
        }

        Path temp = null;
        boolean started = false;
        try {
            temp = Files.createTempFile("badmshot-import-", ".zip");
            file.transferTo(temp);

            SessionManifest manifest = reader.readManifest(temp);
            String sessionName = manifest.getSession().getName();
            if (sessionRepository.existsByName(sessionName)) {
                throw new ArchiveConflictException("Сессия уже существует: " + sessionName);
            }

            ImportOptions options = ImportOptions.builder()
                .archiveFile(temp)
                .originalName(file.getOriginalFilename())
                .build();
            Job job = jobService.start(
                JobService.TYPE_IMPORT,
                JobPhase.IMPORT,
                params(options, file.getSize(), sessionName),
                context -> importTask.run(context, options)
            );
            started = true;
            return job;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить архив: " + e.getMessage(), e);
        } finally {
            if (!started && temp != null) {
                delete(temp);
            }
        }
    }

    Optional<Job> current() {
        return jobService.current(JobService.TYPE_IMPORT);
    }

    List<Job> history(int limit) {
        return jobService.history(JobService.TYPE_IMPORT, limit);
    }

    void cancel() {
        jobService.cancel(JobService.TYPE_IMPORT);
    }

    private Map<String, Object> params(ImportOptions options, long size, String sessionName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fileName", options.getOriginalName());
        params.put("sessionName", sessionName);
        params.put("sizeBytes", size);
        params.put("tempFile", options.getArchiveFile().toString());
        return params;
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Can't delete import file {}: {}", file, e.getMessage());
        }
    }
}
