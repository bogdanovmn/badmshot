package com.github.bogdanovmn.badmshot.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
@RequiredArgsConstructor
class ArchiveImportReader {

    private static final String MANIFEST_NAME = "session.json";
    private static final int MAX_ENTRIES = 2000;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_UNCOMPRESSED_BYTES = 20 * 1024 * 1024;
    private static final int FORMAT_VERSION = 1;
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png", "webp", "avif");

    private final ObjectMapper objectMapper;
    private final ArchivePathParser pathParser;

    SessionManifest readManifest(Path archive) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Map<String, ZipEntry> entries = entries(zip);
            return readManifest(zip, entries);
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать архив: " + e.getMessage(), e);
        }
    }

    LoadedArchive read(Path archive) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Map<String, ZipEntry> entries = entries(zip);
            SessionManifest manifest = readManifest(zip, entries);
            Map<UUID, byte[]> images = images(zip, entries, manifest);
            return LoadedArchive.builder()
                .manifest(manifest)
                .images(images)
                .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать архив: " + e.getMessage(), e);
        }
    }

    private Map<String, ZipEntry> entries(ZipFile zip) {
        Map<String, ZipEntry> entries = new LinkedHashMap<>();
        var enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            if (entries.size() >= MAX_ENTRIES) {
                throw new IllegalArgumentException("В архиве слишком много файлов");
            }
            ZipEntry entry = enumeration.nextElement();
            String name = entry.getName();
            if (entry.isDirectory()
                || name.contains("/")
                || name.contains("\\")
                || name.startsWith("..")
                || name.isBlank()) {
                throw new IllegalArgumentException("Недопустимое имя файла в архиве: " + name);
            }
            if (entries.putIfAbsent(name, entry) != null) {
                throw new IllegalArgumentException("Дублирующийся файл в архиве: " + name);
            }
            if (entry.getSize() > MAX_UNCOMPRESSED_BYTES) {
                throw new IllegalArgumentException("Файл слишком большой: " + name);
            }
        }
        return entries;
    }

    private SessionManifest readManifest(ZipFile zip, Map<String, ZipEntry> entries) {
        ZipEntry entry = entries.get(MANIFEST_NAME);
        if (entry == null) {
            throw new IllegalArgumentException("В архиве отсутствует " + MANIFEST_NAME);
        }
        byte[] data = read(zip, entry, MAX_MANIFEST_BYTES);
        SessionManifest manifest;
        try {
            manifest = objectMapper.readValue(data, SessionManifest.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Некорректный " + MANIFEST_NAME + ": " + e.getMessage(), e);
        }
        validateManifest(manifest);
        return manifest;
    }

    private Map<UUID, byte[]> images(
        ZipFile zip,
        Map<String, ZipEntry> entries,
        SessionManifest manifest
    ) {
        Set<String> expected = new HashSet<>();
        expected.add(MANIFEST_NAME);
        Map<UUID, byte[]> images = new LinkedHashMap<>();
        long total = 0;

        for (SessionManifestMatch match : manifest.getMatches()) {
            for (SessionManifestScreenshot screenshot : match.getScreenshots()) {
                String name = screenshot.getId() + "." + normalizedFormat(screenshot.getFormat());
                expected.add(name);
                ZipEntry entry = entries.get(name);
                if (entry == null) {
                    throw new IllegalArgumentException("В архиве отсутствует файл кадра: " + name);
                }
                byte[] image = read(zip, entry, MAX_UNCOMPRESSED_BYTES - total);
                if (image.length == 0) {
                    throw new IllegalArgumentException("Пустой файл кадра: " + name);
                }
                total += image.length;
                images.put(screenshot.getId(), image);
            }
        }

        Set<String> unknown = new HashSet<>(entries.keySet());
        unknown.removeAll(expected);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("В архиве есть лишние файлы: " + unknown);
        }
        return images;
    }

    private void validateManifest(SessionManifest manifest) {
        if (manifest == null || manifest.getFormatVersion() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Неподдерживаемая версия формата архива");
        }
        SessionManifestSession session = manifest.getSession();
        ArchiveType type = sessionType(session);
        if (session == null
            || session.getName() == null
            || session.getName().isBlank()
            || session.getMatchDate() == null
            || (type == ArchiveType.GAMES
                && (session.getClub() == null || session.getClub().isBlank()))) {
            throw new IllegalArgumentException("В архиве отсутствуют обязательные данные сессии");
        }
        if (manifest.getMatches() == null || manifest.getMatches().isEmpty()) {
            throw new IllegalArgumentException("В сессии нет матчей");
        }

        Set<String> relPaths = new HashSet<>();
        Set<UUID> screenshotIds = new HashSet<>();
        for (SessionManifestMatch match : manifest.getMatches()) {
            if (match == null
                || match.getRelPath() == null
                || match.getRelPath().isBlank()
                || match.getSizeMbytes() < 0
                || match.getPlayers() == null
                || !validPlayerCount(type, match.getPlayers().size())
                || match.getScreenshots() == null
                || match.getScreenshots().isEmpty()) {
                throw new IllegalArgumentException("Некорректное описание матча");
            }
            if (type == ArchiveType.INDI
                && match.getPlayers().size() == 1
                && !ArchivePathParser.DEFAULT_INDI_PLAYER.equals(match.getPlayers().get(0))) {
                throw new IllegalArgumentException(
                    "Для индивидуальной тренировки с одним игроком ожидается "
                        + ArchivePathParser.DEFAULT_INDI_PLAYER
                );
            }
            if (!relPaths.add(match.getRelPath())) {
                throw new IllegalArgumentException("Дублирующийся матч: " + match.getRelPath());
            }
            validateMatchPath(match, session, type);
            for (SessionManifestScreenshot screenshot : match.getScreenshots()) {
                if (screenshot == null
                    || screenshot.getId() == null
                    || screenshot.getTimecodeSeconds() < 0
                    || screenshot.getWidth() <= 0
                    || screenshot.getHeight() <= 0
                    || !ALLOWED_FORMATS.contains(normalizedFormat(screenshot.getFormat()))) {
                    throw new IllegalArgumentException("Некорректное описание кадра");
                }
                if (!screenshotIds.add(screenshot.getId())) {
                    throw new IllegalArgumentException("Дублирующийся кадр: " + screenshot.getId());
                }
            }
        }
    }

    private void validateMatchPath(
        SessionManifestMatch match,
        SessionManifestSession session,
        ArchiveType type
    ) {
        ArchivePath parsed = pathParser.parse(match.getRelPath());
        if (!parsed.valid()) {
            throw new IllegalArgumentException(
                "Некорректный путь матча " + match.getRelPath() + ": " + parsed.getValidationError()
            );
        }
        boolean sameSession = session.getName().equals(parsed.getSessionName())
            && parsed.getType() == type;
        if (sameSession && type == ArchiveType.GAMES) {
            sameSession = session.getMatchDate().equals(parsed.getMatchDate())
                && clubMatches(session, parsed)
                && session.isTournament() == parsed.isTournament();
        } else if (sameSession) {
            sameSession = session.getMatchDate().getYear() == parsed.getMatchDate().getYear()
                && session.getMatchDate().getMonth() == parsed.getMatchDate().getMonth();
        }
        if (!sameSession) {
            throw new IllegalArgumentException("Матч не соответствует сессии: " + match.getRelPath());
        }
        if (!match.getPlayers().equals(parsed.getPlayers())) {
            throw new IllegalArgumentException("Игроки не соответствуют имени файла: " + match.getRelPath());
        }
    }

    private boolean clubMatches(SessionManifestSession session, ArchivePath parsed) {
        if (session.getClub() == null || parsed.getClub() == null) {
            return false;
        }
        if (session.getClub().equals(parsed.getClub())) {
            return session.getGroup() == null || session.getGroup().equals(parsed.getGroup());
        }
        // обратная совместимость: в старых манифестах метка группы входила в название клуба
        return session.getGroup() == null
            && parsed.getGroup() != null
            && session.getClub().equals(parsed.getClub() + "-" + parsed.getGroup());
    }

    private byte[] read(ZipFile zip, ZipEntry entry, long limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Архив слишком большой после распаковки");
        }
        try (InputStream input = zip.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) {
                    throw new IllegalArgumentException("Архив слишком большой после распаковки");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать файл архива: " + entry.getName(), e);
        }
    }

    private ArchiveType sessionType(SessionManifestSession session) {
        return session == null || session.getType() == null
            ? ArchiveType.GAMES
            : session.getType();
    }

    private boolean validPlayerCount(ArchiveType type, int size) {
        if (type == ArchiveType.INDI) {
            return size == 1 || size == 2;
        }
        return size == 2 || size == 4;
    }

    private String normalizedFormat(String format) {
        return format == null ? "" : format.toLowerCase(Locale.ROOT);
    }
}
