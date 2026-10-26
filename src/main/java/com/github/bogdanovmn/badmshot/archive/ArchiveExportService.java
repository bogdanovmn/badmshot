package com.github.bogdanovmn.badmshot.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bogdanovmn.badmshot.content.ScreenshotExport;
import com.github.bogdanovmn.badmshot.content.ScreenshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
class ArchiveExportService {

    private static final int FORMAT_VERSION = 1;

    private final ArchiveSessionRepository sessionRepository;
    private final ScreenshotRepository screenshotRepository;
    private final ArchivePathParser pathParser;
    private final ObjectMapper objectMapper;

    @Value("${badmshot.export.enabled:true}")
    private boolean exportEnabled;

    @Transactional(readOnly = true)
    ArchiveFile export(UUID sessionId) {
        if (!exportEnabled) {
            throw new IllegalStateException("Экспорт отключён");
        }
        ArchiveSession session = sessionRepository.find(sessionId)
            .orElseThrow(() -> new NoSuchElementException("Сессия не найдена: " + sessionId));
        if (!session.isExportable()) {
            throw new ArchiveConflictException("Сессия содержит невалидные матчи или матчи без кадров");
        }
        List<ScreenshotExport> screenshots = screenshotRepository.exportBySession(sessionId);
        Map<UUID, List<ScreenshotExport>> byMatch = new LinkedHashMap<>();
        screenshots.forEach(screenshot -> byMatch
            .computeIfAbsent(screenshot.getMatchId(), id -> new ArrayList<>())
            .add(screenshot)
        );

        SessionManifest manifest = manifest(session, byMatch);

        try {
            byte[] data = zip(manifest, screenshots);
            return ArchiveFile.builder()
                .name(session.getName() + ".zip")
                .data(data)
                .build();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать архив: " + e.getMessage(), e);
        }
    }

    private SessionManifest manifest(
        ArchiveSession session,
        Map<UUID, List<ScreenshotExport>> screenshots
    ) {
        List<SessionManifestMatch> matches = session.getFiles().stream()
            .map(file -> SessionManifestMatch.builder()
                .relPath(file.getRelPath())
                .durationSeconds(file.getDurationSeconds())
                .sizeMbytes(file.getSizeMbytes())
                .players(pathParser.parse(file.getRelPath()).getPlayers())
                .comment(file.getComment())
                .screenshots(screenshots.getOrDefault(file.getId(), List.of()).stream()
                    .map(screenshot -> SessionManifestScreenshot.builder()
                        .id(screenshot.getId())
                        .timecodeSeconds(screenshot.getTimecodeSeconds())
                        .format(normalizedFormat(screenshot.getImageFormat()))
                        .width(screenshot.getWidth())
                        .height(screenshot.getHeight())
                        .build()
                    )
                    .toList()
                )
                .build()
            )
            .toList();

        return SessionManifest.builder()
            .formatVersion(FORMAT_VERSION)
            .session(SessionManifestSession.builder()
                .type(session.getType())
                .name(session.getName())
                .matchDate(session.getMatchDate())
                .club(session.getClub())
                .tournament(session.isTournament())
                .group(session.getGroup())
                .build()
            )
            .matches(matches)
            .build();
    }

    private byte[] zip(SessionManifest manifest, List<ScreenshotExport> screenshots) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("session.json"));
            zip.write(objectMapper.writeValueAsBytes(manifest));
            zip.closeEntry();

            for (ScreenshotExport screenshot : screenshots) {
                zip.putNextEntry(new ZipEntry(
                    screenshot.getId() + "." + normalizedFormat(screenshot.getImageFormat())
                ));
                zip.write(screenshot.getData());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private String normalizedFormat(String format) {
        return format.toLowerCase(Locale.ROOT);
    }
}
