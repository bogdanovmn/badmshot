package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import com.github.bogdanovmn.badmshot.content.ScreenshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ArchiveService {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final ScreenshotRepository screenshotRepository;
    private final StatisticsRepository statisticsRepository;

    @Value("${badmshot.archive-dir}")
    private String archiveDir;

    @Value("${badmshot.archive.read-only:false}")
    private boolean readOnly;

    @Transactional(readOnly = true)
    List<ArchiveYear> tree() {
        return matchRepository.tree();
    }

    @Transactional(readOnly = true)
    MatchPage briefs(Integer year, Integer month, UUID clubId, UUID playerId, Boolean invalid, int limit, int offset) {
        UUID canonicalPlayerId = playerId == null
            ? null
            : playerRepository.canonicalId(playerId).orElse(playerId);
        return MatchPage.builder()
            .total(matchRepository.countBriefs(year, month, clubId, canonicalPlayerId, invalid))
            .items(matchRepository.briefs(year, month, clubId, canonicalPlayerId, invalid, limit, offset))
            .build();
    }

    @Transactional(readOnly = true)
    MatchDetail detail(UUID matchId) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NoSuchElementException("Матч не найден: " + matchId));
        return MatchDetail.builder()
            .match(match)
            .players(playerRepository.byMatch(matchId))
            .screenshots(screenshotRepository.byMatch(matchId))
            .build();
    }

    @Transactional(readOnly = true)
    ArchiveStats stats() {
        return statisticsRepository.stats();
    }

    @Transactional
    MatchBrief rename(UUID matchId, List<String> players) {
        if (readOnly) {
            throw new IllegalStateException("Архив доступен только для чтения");
        }
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NoSuchElementException("Матч не найден: " + matchId));
        if (!match.isValid()) {
            throw new IllegalArgumentException("Нельзя переименовать невалидный файл");
        }
        List<String> cleaned = players == null
            ? List.of()
            : players.stream().map(String::trim).filter(name -> !name.isBlank()).toList();
        if (cleaned.size() != 2 && cleaned.size() != 4) {
            throw new IllegalArgumentException("Ожидается 2 или 4 игрока, получено: " + cleaned.size());
        }

        String relPath = match.getRelPath();
        String newRelPath = buildRelPath(relPath, cleaned);
        if (newRelPath.equals(relPath)) {
            return matchRepository.brief(matchId).orElseThrow();
        }

        Path source = workspace().resolve(relPath);
        Path target = workspace().resolve(newRelPath);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Файл уже существует: " + newRelPath);
        }
        if (matchRepository.existsByRelPath(newRelPath)) {
            throw new IllegalArgumentException("Файл уже проиндексирован: " + newRelPath);
        }
        move(source, target);

        matchRepository.updateRelPath(matchId, newRelPath);
        List<UUID> playerIds = cleaned.stream().map(playerRepository::upsert).toList();
        matchRepository.replacePlayers(matchId, playerIds);
        return matchRepository.brief(matchId).orElseThrow();
    }

    private String buildRelPath(String relPath, List<String> players) {
        int slash = relPath.lastIndexOf('/');
        String parent = slash < 0 ? "" : relPath.substring(0, slash);
        String fileName = relPath.substring(slash + 1);
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? ".mp4" : fileName.substring(dot);
        String dateDir = parent.substring(parent.lastIndexOf('/') + 1);
        String name = dateDir + "--" + String.join("-", players) + extension;
        return parent.isEmpty() ? name : parent + "/" + name;
    }

    private void move(Path source, Path target) {
        try {
            Files.move(source, target);
        } catch (IOException e) {
            log.error("Can't rename {} to {}: {}", source, target, e.getMessage());
            throw new IllegalArgumentException("Не удалось переименовать файл: " + e.getMessage());
        }
    }

    private Path workspace() {
        return Path.of(archiveDir);
    }
}
