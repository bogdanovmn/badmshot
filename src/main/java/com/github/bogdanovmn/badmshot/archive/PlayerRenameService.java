package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.Player;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerRenameService {

    private static final int MAX_NAME_LENGTH = 200;

    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final ArchivePathParser pathParser;

    @Value("${badmshot.archive-dir}")
    private String archiveDir;

    @Value("${badmshot.archive.read-only:false}")
    private boolean readOnly;

    @Transactional(readOnly = true)
    public PlayerRenameInfo info(UUID playerId) {
        Player player = requirePlayer(playerId);
        Map<UUID, List<Integer>> selectedPositions = new LinkedHashMap<>();
        for (MatchPlayerSlot slot : matchRepository.playerSlots(playerId)) {
            selectedPositions.computeIfAbsent(slot.getMatchId(), id -> new ArrayList<>())
                .add(slot.getDisplayPosition());
        }
        List<PlayerRenameMatch> matches = matchRepository.byPlayer(playerId).stream()
            .map(match -> PlayerRenameMatch.builder()
                .id(match.getId())
                .relPath(match.getRelPath())
                .club(club(match.getRelPath()))
                .players(match.getPlayers())
                .selectedPositions(selectedPositions.getOrDefault(match.getId(), List.of()))
                .build())
            .toList();
        return PlayerRenameInfo.builder()
            .player(player)
            .matches(matches)
            .build();
    }

    @Transactional
    public PlayerRenameResult rename(UUID playerId, String rawName) {
        if (readOnly) {
            throw new IllegalStateException("Архив доступен только для чтения");
        }
        Player player = requirePlayer(playerId);
        if (player.isAlias()) {
            throw new IllegalArgumentException("Игрок является псевдонимом другого игрока");
        }
        String newName = normalizeName(rawName);
        if (newName.equals(player.getName())) {
            return result(newName, 0);
        }
        Optional<UUID> targetPlayerId = playerRepository.findIdByName(newName, playerId);

        List<RenameAction> actions = renameActions(playerId, newName);
        preflight(actions);
        for (RenameAction action : actions) {
            move(workspace().resolve(action.relPath()), workspace().resolve(action.newRelPath()));
            matchRepository.updateRelPath(action.matchId(), action.newRelPath());
        }
        if (targetPlayerId.isPresent()) {
            matchRepository.reassignPlayer(playerId, targetPlayerId.get());
            playerRepository.mergeInto(playerId, targetPlayerId.get());
            log.info(
                "Player {} merged into {} ({} files renamed)",
                player.getName(), newName, actions.size()
            );
        } else {
            playerRepository.updateName(playerId, newName);
            log.info("Player {} renamed to {} in {} files", player.getName(), newName, actions.size());
        }
        return result(newName, actions.size());
    }

    private List<RenameAction> renameActions(UUID playerId, String newName) {
        Map<UUID, MatchPlayerSlot> matches = new LinkedHashMap<>();
        Map<UUID, List<Integer>> positions = new LinkedHashMap<>();
        for (MatchPlayerSlot slot : matchRepository.playerSlots(playerId)) {
            matches.putIfAbsent(slot.getMatchId(), slot);
            positions.computeIfAbsent(slot.getMatchId(), id -> new ArrayList<>())
                .add(slot.getPosition());
        }

        List<RenameAction> actions = new ArrayList<>();
        for (Map.Entry<UUID, List<Integer>> entry : positions.entrySet()) {
            MatchPlayerSlot slot = matches.get(entry.getKey());
            ArchivePath parsed = pathParser.parse(slot.getRelPath());
            if (!parsed.valid()) {
                throw new IllegalArgumentException(
                    "Нельзя переименовать невалидный файл: " + slot.getRelPath()
                );
            }
            List<String> players = new ArrayList<>(parsed.getPlayers());
            for (int position : entry.getValue()) {
                if (position < 0 || position >= players.size()) {
                    throw new IllegalArgumentException(
                        "Не удалось сопоставить игрока с именем файла: " + slot.getRelPath()
                    );
                }
                players.set(position, newName);
            }
            String newRelPath = ArchivePathBuilder.buildRelPath(
                slot.getRelPath(), players, parsed.getComment(), parsed.getType()
            );
            if (newRelPath.equals(slot.getRelPath())) {
                throw new IllegalArgumentException(
                    "Имя игрока не указано в файле явно, переименование невозможно: "
                        + slot.getRelPath()
                );
            }
            actions.add(new RenameAction(slot.getMatchId(), slot.getRelPath(), newRelPath));
        }
        return actions;
    }

    private void preflight(List<RenameAction> actions) {
        Set<String> targets = new HashSet<>();
        for (RenameAction action : actions) {
            Path target = workspace().resolve(action.newRelPath());
            if (Files.exists(target)) {
                throw new IllegalArgumentException("Файл уже существует: " + action.newRelPath());
            }
            if (!targets.add(action.newRelPath())) {
                throw new IllegalArgumentException("Несколько файлов претендуют на одно имя: "
                    + action.newRelPath());
            }
            if (matchRepository.existsByRelPath(action.newRelPath())) {
                throw new IllegalArgumentException("Файл уже проиндексирован: " + action.newRelPath());
            }
        }
    }

    private Player requirePlayer(UUID playerId) {
        return playerRepository.findById(playerId)
            .orElseThrow(() -> new NoSuchElementException("Игрок не найден: " + playerId));
    }

    private String club(String relPath) {
        return pathParser.parse(relPath).getClub();
    }

    private String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Имя игрока не может быть пустым");
        }
        String name = rawName.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                "Имя игрока слишком длинное (максимум " + MAX_NAME_LENGTH + " символов)"
            );
        }
        if (ArchivePathParser.isSkip(name)) {
            throw new IllegalArgumentException("Имя skip зарезервировано для заглушки");
        }
        if (name.indexOf('-') >= 0 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                "Имя игрока не может содержать символы -, / и \\"
            );
        }
        return name;
    }

    private void move(Path source, Path target) {
        try {
            Files.move(source, target);
        } catch (IOException e) {
            log.error("Can't rename {} to {}: {}", source, target, e.getMessage());
            throw new IllegalArgumentException("Не удалось переименовать файл: " + e.getMessage());
        }
    }

    private PlayerRenameResult result(String name, int renamedFiles) {
        return PlayerRenameResult.builder()
            .name(name)
            .renamedFiles(renamedFiles)
            .build();
    }

    private Path workspace() {
        return Path.of(archiveDir);
    }

    private record RenameAction(UUID matchId, String relPath, String newRelPath) {
    }
}
