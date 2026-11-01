package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.Player;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PlayerDossierService {

    private final PlayerRepository playerRepository;
    private final PlayerDossierRepository dossierRepository;

    @Transactional(readOnly = true)
    PlayerDossier dossier(UUID playerId) {
        UUID canonicalId = playerRepository.canonicalId(playerId)
            .orElseThrow(() -> new NoSuchElementException("Игрок не найден: " + playerId));
        Player player = playerRepository.findById(canonicalId)
            .orElseThrow(() -> new NoSuchElementException("Игрок не найден: " + playerId));
        return PlayerDossier.builder()
            .player(player)
            .stats(dossierRepository.stats(canonicalId))
            .clubs(dossierRepository.clubs(canonicalId))
            .partners(dossierRepository.partners(canonicalId))
            .opponents(dossierRepository.opponents(canonicalId))
            .build();
    }
}
