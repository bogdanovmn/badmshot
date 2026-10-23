package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ArchiveSessionService {

    private final ArchiveSessionRepository sessionRepository;
    private final PlayerRepository playerRepository;

    @Transactional(readOnly = true)
    ArchiveSessions sessions(Integer year, Integer month, UUID clubId, UUID playerId, ArchiveType type) {
        ArchivePeriod period = period(year, month, type);
        if (period == null) {
            return ArchiveSessions.builder()
                .year(null)
                .month(null)
                .sessions(List.of())
                .build();
        }
        UUID canonicalPlayerId = playerId == null
            ? null
            : playerRepository.canonicalId(playerId).orElse(playerId);
        return ArchiveSessions.builder()
            .year(period.getYear())
            .month(period.getMonth())
            .sessions(sessionRepository.sessions(
                period.getYear(),
                period.getMonth(),
                clubId,
                canonicalPlayerId,
                type
            ))
            .build();
    }

    private ArchivePeriod period(Integer year, Integer month, ArchiveType type) {
        if (year == null && month == null) {
            return sessionRepository.latestPeriod(type).orElse(null);
        }
        if (year == null || month == null) {
            throw new IllegalArgumentException("Параметры year и month должны передаваться вместе");
        }
        if (year < 1 || month < 1 || month > 12) {
            throw new IllegalArgumentException("Некорректный год или месяц");
        }
        return ArchivePeriod.builder()
            .year(year)
            .month(month)
            .build();
    }
}
