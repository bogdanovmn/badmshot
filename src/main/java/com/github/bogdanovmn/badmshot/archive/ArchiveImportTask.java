package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.content.FrameTeaserService;
import com.github.bogdanovmn.badmshot.job.JobContext;
import com.github.bogdanovmn.badmshot.job.JobPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
@Slf4j
class ArchiveImportTask {

    private final ArchiveImportReader reader;
    private final ArchiveImportTransactional transactional;
    private final FrameTeaserService frameTeaserService;

    void run(JobContext context, ImportOptions options) {
        try {
            LoadedArchive archive = reader.read(options.getArchiveFile());
            context.phase(JobPhase.IMPORT);
            context.totalFiles(archive.getManifest().getMatches().size());
            transactional.importSession(archive, context.jobId(), context);
            frameTeaserService.invalidate();
        } finally {
            delete(options.getArchiveFile());
        }
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Can't delete import file {}: {}", file, e.getMessage());
        }
    }
}
