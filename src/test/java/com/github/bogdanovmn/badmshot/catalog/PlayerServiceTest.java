package com.github.bogdanovmn.badmshot.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerService playerService;

    private final UUID playerId = UUID.randomUUID();

    @Test
    void rejectsSelfAlias() {
        when(playerRepository.findById(playerId))
            .thenReturn(Optional.of(player(playerId, null, null)));

        assertThrows(
            IllegalArgumentException.class,
            () -> playerService.update(playerId, update("Игрок", playerId))
        );

        verify(playerRepository, never()).updateDisplayName(playerId, "Игрок");
    }

    @Test
    void rejectsAliasOfAnotherPlayer() {
        UUID anotherPlayerId = UUID.randomUUID();
        UUID aliasId = UUID.randomUUID();
        when(playerRepository.findById(playerId))
            .thenReturn(Optional.of(player(playerId, null, null)));
        when(playerRepository.findById(aliasId))
            .thenReturn(Optional.of(player(aliasId, anotherPlayerId, null)));

        assertThrows(
            IllegalArgumentException.class,
            () -> playerService.update(playerId, update("Игрок", aliasId))
        );
    }

    @Test
    void rejectsPlayerWithOwnAlias() {
        UUID candidateId = UUID.randomUUID();
        when(playerRepository.findById(playerId))
            .thenReturn(Optional.of(player(playerId, null, null)));
        when(playerRepository.findById(candidateId))
            .thenReturn(Optional.of(player(
                candidateId,
                null,
                PlayerAlias.builder().id(UUID.randomUUID()).name("candidate-alias").build()
            )));

        assertThrows(
            IllegalArgumentException.class,
            () -> playerService.update(playerId, update("Игрок", candidateId))
        );
    }

    @Test
    void keepsExistingAliasWhenItIsResent() {
        UUID aliasId = UUID.randomUUID();
        when(playerRepository.findById(playerId))
            .thenReturn(Optional.of(player(playerId, null, null)));
        when(playerRepository.findById(aliasId))
            .thenReturn(Optional.of(player(aliasId, playerId, null)));

        playerService.update(playerId, update("Игрок", aliasId));

        verify(playerRepository).updateDisplayName(playerId, "Игрок");
        verify(playerRepository).clearAlias(playerId);
        verify(playerRepository).setAlias(playerId, aliasId);
    }

    @Test
    void clearsAliasWhenAliasIsNotPassed() {
        when(playerRepository.findById(playerId))
            .thenReturn(Optional.of(player(playerId, null, null)));

        playerService.update(playerId, update(null, null));

        verify(playerRepository).updateDisplayName(playerId, null);
        verify(playerRepository).clearAlias(playerId);
        verify(playerRepository, never()).setAlias(playerId, playerId);
    }

    private Player player(UUID id, UUID aliasOfPlayerId, PlayerAlias alias) {
        return Player.builder()
            .id(id)
            .name("player-" + id)
            .aliasOfPlayerId(aliasOfPlayerId)
            .alias(alias)
            .build();
    }

    private PlayerUpdate update(String displayName, UUID aliasPlayerId) {
        return PlayerUpdate.builder()
            .displayName(displayName)
            .aliasPlayerId(aliasPlayerId)
            .build();
    }
}
