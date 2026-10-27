package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import com.github.bogdanovmn.badmshot.content.Screenshot;
import com.github.bogdanovmn.badmshot.content.ScreenshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class ArchiveService {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final ScreenshotRepository screenshotRepository;
    private final StatisticsRepository statisticsRepository;
    private final ArchivePathParser pathParser;

    @Value("${badmshot.archive-dir}")
    private String archiveDir;

    @Value("${badmshot.archive.files-read-only:false}")
    private boolean filesReadOnly;

    @Transactional(readOnly = true)
    List<ArchiveYear> tree(ArchiveType type, UUID playerId) {
        UUID canonicalPlayerId = playerId == null
            ? null
            : playerRepository.canonicalId(playerId).orElse(playerId);
        return matchRepository.tree(type, canonicalPlayerId);
    }

    @Transactional(readOnly = true)
    MatchPage briefs(
        Integer year,
        Integer month,
        UUID clubId,
        UUID playerId,
        Boolean invalid,
        ArchiveType type,
        int limit,
        int offset
    ) {
        UUID canonicalPlayerId = playerId == null
            ? null
            : playerRepository.canonicalId(playerId).orElse(playerId);
        return MatchPage.builder()
            .total(matchRepository.countBriefs(year, month, clubId, canonicalPlayerId, invalid, type))
            .items(matchRepository.briefs(
                year, month, clubId, canonicalPlayerId, invalid, type, limit, offset
            ))
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
    ArchiveStats stats(ArchiveType type) {
        return statisticsRepository.stats(type);
    }

    @Transactional
    MatchBrief rename(UUID matchId, List<String> players) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NoSuchElementException("Матч не найден: " + matchId));
        if (!match.isValid()) {
            throw new IllegalArgumentException("Нельзя переименовать невалидный файл");
        }
        List<String> cleaned = players == null
            ? List.of()
            : players.stream().map(String::trim).filter(name -> !name.isBlank()).toList();
        ArchivePath parsed = pathParser.parse(match.getRelPath());
        List<String> merged = mergeWithPlaceholders(parsed.getPlayers(), cleaned);
        validatePlayers(merged, match.getType());

        String relPath = match.getRelPath();
        if (!filesReadOnly) {
            String newRelPath = ArchivePathBuilder.buildRelPath(
                relPath, merged, parsed.getComment(), match.getType()
            );
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
        } else if (merged.equals(parsed.getPlayers())) {
            return matchRepository.brief(matchId).orElseThrow();
        }
        List<UUID> playerIds = merged.stream()
            .map(name -> ArchivePathParser.isSkip(name) ? null : playerRepository.upsert(name))
            .collect(Collectors.toList());
        matchRepository.replacePlayers(matchId, playerIds);
        return matchRepository.brief(matchId).orElseThrow();
    }

    private List<String> mergeWithPlaceholders(List<String> current, List<String> edited) {
        long visible = current.stream().filter(name -> !ArchivePathParser.isSkip(name)).count();
        if (visible == current.size()) {
            return edited;
        }
        if (edited.size() != visible) {
            throw new IllegalArgumentException(
                "Количество игроков не совпадает с файлом: ожидается " + visible
                    + " (позиции-заглушки skip сохраняются отдельно)"
            );
        }
        List<String> merged = new ArrayList<>(current.size());
        int editedIndex = 0;
        for (String name : current) {
            merged.add(ArchivePathParser.isSkip(name)
                ? ArchivePathParser.SKIP_PLAYER
                : edited.get(editedIndex++));
        }
        return merged;
    }

    private void validatePlayers(List<String> players, ArchiveType type) {
        if (type == ArchiveType.INDI) {
            boolean defaultPlayer = players.size() == 1
                && ArchivePathParser.DEFAULT_INDI_PLAYER.equals(players.get(0));
            if (players.size() != 2 && !defaultPlayer) {
                throw new IllegalArgumentException(
                    "Ожидается 2 игрока (или 1 игрок по умолчанию), получено: " + players.size()
                );
            }
        } else if (players.size() != 2 && players.size() != 4) {
            throw new IllegalArgumentException("Ожидается 2 или 4 игрока, получено: " + players.size());
        }
    }

    @Transactional
    void deleteMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NoSuchElementException("Матч не найден: " + matchId));
        matchRepository.deleteById(matchId);
        if (match.getSessionId() != null) {
            matchRepository.deleteSessionIfEmpty(match.getSessionId());
        }
    }

    @Transactional
    void deleteScreenshot(UUID matchId, UUID screenshotId) {
        Screenshot screenshot = screenshotRepository.findById(screenshotId)
            .orElseThrow(() -> new NoSuchElementException("Кадр не найден: " + screenshotId));
        if (!screenshot.getMatchId().equals(matchId)) {
            throw new IllegalArgumentException("Кадр не принадлежит указанному матчу");
        }
        screenshotRepository.deleteById(screenshotId);
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
