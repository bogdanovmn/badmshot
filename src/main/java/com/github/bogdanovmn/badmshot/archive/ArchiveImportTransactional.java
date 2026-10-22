package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.ClubRepository;
import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import com.github.bogdanovmn.badmshot.content.ScreenshotRepository;
import com.github.bogdanovmn.badmshot.job.JobCancelledException;
import com.github.bogdanovmn.badmshot.job.JobContext;
import com.github.bogdanovmn.badmshot.job.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ArchiveImportTransactional {

    private final ArchiveSessionRepository sessionRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final ScreenshotRepository screenshotRepository;
    private final JobService jobService;

    @Transactional
    void importSession(LoadedArchive archive, UUID jobId, JobContext context) {
        SessionManifest manifest = archive.getManifest();
        SessionManifestSession session = manifest.getSession();

        UUID clubId = clubRepository.upsert(session.getClub());
        UUID sessionId = sessionRepository.insert(
            session.getName(),
            session.getMatchDate(),
            clubId,
            session.isTournament()
        );

        int processed = 0;
        int screenshots = 0;
        for (SessionManifestMatch match : manifest.getMatches()) {
            if (context.cancelled()) {
                throw new JobCancelledException();
            }
            UUID matchId = matchRepository.insertImported(
                sessionId,
                match.getRelPath(),
                match.getDurationSeconds(),
                match.getSizeMbytes()
            );
            List<UUID> playerIds = match.getPlayers().stream()
                .map(playerRepository::upsert)
                .toList();
            matchRepository.replacePlayers(matchId, playerIds);

            for (SessionManifestScreenshot screenshot : match.getScreenshots()) {
                byte[] image = archive.getImages().get(screenshot.getId());
                if (image == null) {
                    throw new IllegalArgumentException("Не найден кадр: " + screenshot.getId());
                }
                screenshotRepository.insertImported(
                    screenshot.getId(),
                    matchId,
                    screenshot.getTimecodeSeconds(),
                    image,
                    screenshot.getFormat().toLowerCase(Locale.ROOT),
                    screenshot.getWidth(),
                    screenshot.getHeight()
                );
                screenshots++;
            }
            processed++;
            jobService.updateProgressNow(jobId, processed, screenshots);
        }
    }
}
