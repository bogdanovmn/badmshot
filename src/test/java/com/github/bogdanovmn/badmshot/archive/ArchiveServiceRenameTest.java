package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import com.github.bogdanovmn.badmshot.content.ScreenshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceRenameTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ScreenshotRepository screenshotRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @TempDir
    private Path archiveDir;

    private ArchiveService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ArchiveService(
            matchRepository,
            playerRepository,
            screenshotRepository,
            statisticsRepository,
            new ArchivePathParser()
        );
        Field archiveDirField = ArchiveService.class.getDeclaredField("archiveDir");
        archiveDirField.setAccessible(true);
        archiveDirField.set(service, archiveDir.toString());
    }

    @Test
    void keepsSkipPositionsAndCommentWhenRenaming() throws Exception {
        String relPath = "games/2024/2024-04/2024-04-17-lesteh/"
            + "2024-04-17-lesteh--olyaZ-misha-skip-skip--note.mp4";
        Path source = archiveDir.resolve(relPath);
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[0]);

        UUID matchId = UUID.randomUUID();
        UUID olyaNewId = UUID.randomUUID();
        UUID mishaId = UUID.randomUUID();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(
            Match.builder()
                .id(matchId)
                .type(ArchiveType.GAMES)
                .relPath(relPath)
                .build()
        ));
        when(playerRepository.upsert(anyString())).thenAnswer(invocation -> Map.of(
            "olyaZ2", olyaNewId,
            "misha", mishaId
        ).get(invocation.getArgument(0, String.class)));
        when(matchRepository.brief(matchId)).thenReturn(Optional.of(
            MatchBrief.builder()
                .id(matchId)
                .type(ArchiveType.GAMES)
                .relPath("renamed")
                .build()
        ));

        service.rename(matchId, List.of("olyaZ2", "misha"));

        String expectedRelPath = "games/2024/2024-04/2024-04-17-lesteh/"
            + "2024-04-17-lesteh--olyaZ2-misha-skip-skip--note.mp4";
        verify(matchRepository).updateRelPath(matchId, expectedRelPath);
        assertTrue(Files.exists(archiveDir.resolve(expectedRelPath)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> playersCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).replacePlayers(eq(matchId), playersCaptor.capture());
        assertEquals(Arrays.asList(olyaNewId, mishaId, null, null), playersCaptor.getValue());
    }
}
