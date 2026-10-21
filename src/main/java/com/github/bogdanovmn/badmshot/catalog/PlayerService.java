package com.github.bogdanovmn.badmshot.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PlayerService {

    private final PlayerRepository playerRepository;

    @Transactional
    void update(UUID playerId, PlayerUpdate request) {
        Player player = playerRepository.findById(playerId)
            .orElseThrow(() -> new NoSuchElementException("Игрок не найден: " + playerId));
        if (player.isAlias()) {
            throw new IllegalArgumentException("Игрок является алиасом другого игрока: " + playerId);
        }

        UUID aliasPlayerId = request.getAliasPlayerId();
        if (aliasPlayerId != null) {
            if (aliasPlayerId.equals(playerId)) {
                throw new IllegalArgumentException("Нельзя назначить игрока алиасом самого себя");
            }
            Player alias = playerRepository.findById(aliasPlayerId)
                .orElseThrow(() -> new NoSuchElementException("Игрок не найден: " + aliasPlayerId));
            if (alias.isAlias() && !playerId.equals(alias.getAliasOfPlayerId())) {
                throw new IllegalArgumentException("Игрок уже является алиасом другого игрока: " + aliasPlayerId);
            }
            if (alias.getAlias() != null) {
                throw new IllegalArgumentException("У выбранного игрока уже есть свой алиас: " + aliasPlayerId);
            }
        }

        playerRepository.updateDisplayName(playerId, normalize(request.getDisplayName()));
        playerRepository.clearAlias(playerId);
        if (aliasPlayerId != null) {
            playerRepository.setAlias(playerId, aliasPlayerId);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
