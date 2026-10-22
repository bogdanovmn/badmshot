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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
            "2026/2026-05/2026-05-17-t-badminton-club/"
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
        when(sessionRepository.upsert(anyString(), any(), any(), anyBoolean()))
            .thenReturn(sessionId);
        when(playerRepository.upsert(anyString()))
            .thenAnswer(invocation -> playerIds.get(invocation.getArgument(0, String.class)));
        when(matchRepository.upsertMeta(
            anyString(), any(), anyInt(), any()
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
}
