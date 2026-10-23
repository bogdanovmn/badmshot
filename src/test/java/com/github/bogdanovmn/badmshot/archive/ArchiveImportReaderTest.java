package com.github.bogdanovmn.badmshot.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveImportReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ArchiveImportReader reader = new ArchiveImportReader(
        objectMapper,
        new ArchivePathParser()
    );

    @TempDir
    private Path tempDir;

    @Test
    void readsValidArchive() throws IOException {
        UUID screenshotId = UUID.randomUUID();
        byte[] image = new byte[]{1, 2, 3};
        Path archive = archive(manifest(screenshotId), Map.of(screenshotId + ".jpeg", image));

        LoadedArchive loaded = reader.read(archive);

        assertEquals("2026-05-19-lesteh", loaded.getManifest().getSession().getName());
        assertEquals(1, loaded.getManifest().getMatches().size());
        assertArrayEquals(image, loaded.getImages().get(screenshotId));
    }

    @Test
    void readsIndiArchive() throws IOException {
        UUID screenshotId = UUID.randomUUID();
        Path archive = archive(indiManifest(screenshotId), Map.of(screenshotId + ".webp", new byte[]{4, 5}));

        LoadedArchive loaded = reader.read(archive);

        assertEquals(ArchiveType.INDI, loaded.getManifest().getSession().getType());
        assertEquals("indi-2026-03", loaded.getManifest().getSession().getName());
        assertEquals(List.of("misha"), loaded.getManifest().getMatches().get(0).getPlayers());
        assertArrayEquals(new byte[]{4, 5}, loaded.getImages().get(screenshotId));
    }

    @Test
    void rejectsExtraFiles() throws IOException {
        UUID screenshotId = UUID.randomUUID();
        Path archive = archive(
            manifest(screenshotId),
            Map.of(
                screenshotId + ".jpeg", new byte[]{1},
                "extra.txt", new byte[]{2}
            )
        );

        assertThrows(IllegalArgumentException.class, () -> reader.read(archive));
    }

    @Test
    void rejectsManifestWithoutSessionFiles() throws IOException {
        Path archive = archive(manifest(UUID.randomUUID()), Map.of());

        assertThrows(IllegalArgumentException.class, () -> reader.read(archive));
    }

    private SessionManifest manifest(UUID screenshotId) {
        SessionManifestScreenshot screenshot = SessionManifestScreenshot.builder()
            .id(screenshotId)
            .timecodeSeconds(10)
            .format("jpeg")
            .width(320)
            .height(180)
            .build();
        SessionManifestMatch match = SessionManifestMatch.builder()
            .relPath("games/2026/2026-05/2026-05-19-lesteh/2026-05-19-lesteh--Ivanov-Petrov.mp4")
            .durationSeconds(120)
            .sizeMbytes(20)
            .players(List.of("Ivanov", "Petrov"))
            .screenshots(List.of(screenshot))
            .build();
        return SessionManifest.builder()
            .formatVersion(1)
            .session(SessionManifestSession.builder()
                .name("2026-05-19-lesteh")
                .matchDate(LocalDate.of(2026, 5, 19))
                .club("lesteh")
                .tournament(false)
                .build()
            )
            .matches(List.of(match))
            .build();
    }

    private SessionManifest indiManifest(UUID screenshotId) {
        SessionManifestScreenshot screenshot = SessionManifestScreenshot.builder()
            .id(screenshotId)
            .timecodeSeconds(15)
            .format("webp")
            .width(320)
            .height(180)
            .build();
        SessionManifestMatch match = SessionManifestMatch.builder()
            .relPath("indi/2026/2026-03/2026-03-04-indi.mp4")
            .durationSeconds(300)
            .sizeMbytes(40)
            .players(List.of("misha"))
            .screenshots(List.of(screenshot))
            .build();
        return SessionManifest.builder()
            .formatVersion(1)
            .session(SessionManifestSession.builder()
                .type(ArchiveType.INDI)
                .name("indi-2026-03")
                .matchDate(LocalDate.of(2026, 3, 1))
                .club(null)
                .tournament(false)
                .build()
            )
            .matches(List.of(match))
            .build();
    }

    private Path archive(SessionManifest manifest, Map<String, byte[]> images) throws IOException {
        Path file = tempDir.resolve(UUID.randomUUID() + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("session.json"));
            zip.write(objectMapper.writeValueAsBytes(manifest));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> image : images.entrySet()) {
                zip.putNextEntry(new ZipEntry(image.getKey()));
                zip.write(image.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }
}
