package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.ClubRepository;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import com.github.bogdanovmn.badmshot.content.ScreenshotProducer;
import com.github.bogdanovmn.badmshot.content.ScreenshotRepository;
import com.github.bogdanovmn.badmshot.job.JobContext;
import com.github.bogdanovmn.badmshot.video.VideoProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveIndexTaskTest {

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ArchiveSessionRepository sessionRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ScreenshotRepository screenshotRepository;

    @Mock
    private ScreenshotProducer screenshotProducer;

    @Mock
    private VideoProbe videoProbe;

    @Mock
    private JobContext context;

    @TempDir
    private Path archiveDir;

    private ArchiveIndexTask task;

    @BeforeEach
    void setUp() {
        task = new ArchiveIndexTask(
            new ArchivePathParser(),
            clubRepository,
            playerRepository,
            sessionRepository,
            matchRepository,
            screenshotRepository,
            screenshotProducer,
            videoProbe
        );
    }

    @Test
    void keepsPlayersWithTheSameNameInEveryPosition() throws Exception {
        Path matchFile = archiveDir.resolve(
            "games/2026/2026-05/2026-05-17-t-badminton-club/"
                + "2026-05-17-t-badminton-club--bogdanovM-polyakovE-kulaginA-kulaginA.mp4"
        );
        Files.createDirectories(matchFile.getParent());
        Files.write(matchFile, new byte[0]);

        UUID clubId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID bogdanovId = UUID.randomUUID();
        UUID polyakovId = UUID.randomUUID();
        UUID kulaginId = UUID.randomUUID();
        Map<String, UUID> playerIds = Map.of(
            "bogdanovM", bogdanovId,
            "polyakovE", polyakovId,
            "kulaginA", kulaginId
        );

        when(clubRepository.upsert("badminton-club")).thenReturn(clubId);
        when(sessionRepository.upsert(anyString(), any(), any(), anyBoolean(), any(), any()))
            .thenReturn(sessionId);
        when(playerRepository.upsert(anyString()))
            .thenAnswer(invocation -> playerIds.get(invocation.getArgument(0, String.class)));
        when(matchRepository.upsertMeta(
            anyString(), any(), anyInt(), any(), any(), any()
        )).thenReturn(matchId);

        task.run(
            context,
            IndexOptions.builder()
                .mode(IndexMode.META)
                .archiveDir(archiveDir)
                .build()
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> playersCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).replacePlayers(eq(matchId), playersCaptor.capture());
        assertEquals(
            List.of(bogdanovId, polyakovId, kulaginId, kulaginId),
            playersCaptor.getValue()
        );
        verify(playerRepository, times(4)).upsert(anyString());
    }

    @Test
    void keepsSkipPlayerPositionsWithoutCatalogEntries() throws Exception {
        Path matchFile = archiveDir.resolve(
            "games/2024/2024-04/2024-04-17-lesteh/"
                + "2024-04-17-lesteh--olyaZ-misha-skip-skip.mp4"
        );
        Files.createDirectories(matchFile.getParent());
        Files.write(matchFile, new byte[0]);

        UUID sessionId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID olyaId = UUID.randomUUID();
        UUID mishaId = UUID.randomUUID();

        when(clubRepository.upsert("lesteh")).thenReturn(UUID.randomUUID());
        when(sessionRepository.upsert(anyString(), any(), any(), anyBoolean(), any(), any()))
            .thenReturn(sessionId);
        when(playerRepository.upsert(anyString())).thenReturn(olyaId, mishaId);
        when(matchRepository.upsertMeta(
            anyString(), any(), anyInt(), any(), any(), any()
        )).thenReturn(matchId);

        task.run(
            context,
            IndexOptions.builder()
                .mode(IndexMode.META)
                .archiveDir(archiveDir)
                .build()
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> playersCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).replacePlayers(eq(matchId), playersCaptor.capture());
        assertEquals(Arrays.asList(olyaId, mishaId, null, null), playersCaptor.getValue());
        verify(playerRepository, times(2)).upsert(anyString());
        verify(playerRepository, never()).upsert("skip");
    }

    @Test
    void passesGroupAndCommentToRepositories() throws Exception {
        String relPath = "games/2026/2026-08/2026-08-16-t-newton-XDF/"
            + "2026-08-16-t-newton-XDF--bogdanovM-penevaE-brehovskyhV-markovaA--blablablaIgnoreMe.mp4";
        Path matchFile = archiveDir.resolve(relPath);
        Files.createDirectories(matchFile.getParent());
        Files.write(matchFile, new byte[0]);

        UUID sessionId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();

        when(clubRepository.upsert("newton")).thenReturn(UUID.randomUUID());
        when(sessionRepository.upsert(anyString(), any(), any(), anyBoolean(), any(), any()))
            .thenReturn(sessionId);
        when(playerRepository.upsert(anyString())).thenReturn(UUID.randomUUID());
        when(matchRepository.upsertMeta(
            anyString(), any(), anyInt(), any(), any(), any()
        )).thenReturn(matchId);

        task.run(
            context,
            IndexOptions.builder()
                .mode(IndexMode.META)
                .archiveDir(archiveDir)
                .build()
        );

        verify(sessionRepository).upsert(
            eq("2026-08-16-t-newton-XDF"),
            any(),
            any(),
            eq(true),
            eq("XDF"),
            eq(ArchiveType.GAMES)
        );
        verify(matchRepository).upsertMeta(
            eq(relPath),
            eq(sessionId),
            anyInt(),
            isNull(),
            eq("blablablaIgnoreMe"),
            eq(ArchiveType.GAMES)
        );
    }
}
