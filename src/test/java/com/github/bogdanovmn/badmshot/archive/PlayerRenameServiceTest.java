package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.Player;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerRenameServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @TempDir
    private Path archiveDir;

    private PlayerRenameService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PlayerRenameService(
            playerRepository,
            matchRepository,
            new ArchivePathParser()
        );
        Field archiveDirField = PlayerRenameService.class.getDeclaredField("archiveDir");
        archiveDirField.setAccessible(true);
        archiveDirField.set(service, archiveDir.toString());
    }

    @Test
    void renamesPlayerInFileAndKeepsOtherNamesAndComment() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        String relPath = "games/2024/2024-04/2024-04-17-lesteh/"
            + "2024-04-17-lesteh--olyaZ-misha-skip-skip--note.mp4";
        Path source = archiveDir.resolve(relPath);
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[0]);

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(
            Player.builder()
                .id(playerId)
                .name("olyaZ")
                .build()
        ));
        when(matchRepository.playerSlots(playerId)).thenReturn(List.of(
            MatchPlayerSlot.builder()
                .matchId(matchId)
                .type(ArchiveType.GAMES)
                .relPath(relPath)
                .position(0)
                .displayPosition(0)
                .build()
        ));

        PlayerRenameResult result = service.rename(playerId, "olyaZ2");

        String expectedRelPath = "games/2024/2024-04/2024-04-17-lesteh/"
            + "2024-04-17-lesteh--olyaZ2-misha-skip-skip--note.mp4";
        assertEquals("olyaZ2", result.getName());
        assertEquals(1, result.getRenamedFiles());
        assertTrue(Files.exists(archiveDir.resolve(expectedRelPath)));
        assertFalse(Files.exists(source));
        verify(matchRepository).updateRelPath(matchId, expectedRelPath);
        verify(playerRepository).updateName(playerId, "olyaZ2");
    }
}
