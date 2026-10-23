package com.github.bogdanovmn.badmshot.archive;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class ArchiveSessionRepository {

    private static final RowMapper<ArchiveSession> SESSION_ROW_MAPPER = (rs, rowNum) -> ArchiveSession.builder()
        .id(UUID.fromString(rs.getString("id")))
        .type(ArchiveType.valueOf(rs.getString("type")))
        .name(rs.getString("name"))
        .matchDate(rs.getObject("match_date", LocalDate.class))
        .year((Integer) rs.getObject("year"))
        .month((Integer) rs.getObject("month"))
        .clubId(rs.getString("club_id") == null ? null : UUID.fromString(rs.getString("club_id")))
        .club(rs.getString("club"))
        .clubName(rs.getString("club_name"))
        .tournament(rs.getBoolean("tournament"))
        .matches(rs.getInt("matches"))
        .sizeMbytes(rs.getLong("size_mbytes"))
        .durationSeconds(rs.getLong("duration_seconds"))
        .invalid(rs.getInt("invalid"))
        .exportable(rs.getBoolean("exportable"))
        .files(List.of())
        .build();

    private static final String SESSION_SELECT = """
        SELECT s.id, s.type, s.name, s.match_date, s.year, s.month, s.club_id, s.tournament,
               c.name AS club,
               coalesce(c.display_name, c.name) AS club_name,
               count(m.id) AS matches,
               coalesce(sum(m.size_mbytes), 0) AS size_mbytes,
               coalesce(sum(m.duration_seconds), 0) AS duration_seconds,
               count(*) FILTER (WHERE m.validation_error IS NOT NULL) AS invalid,
               count(m.id) > 0
                   AND bool_and(m.validation_error IS NULL)
                   AND bool_and(EXISTS (
                       SELECT 1
                       FROM screenshot sh
                       WHERE sh.match_id = m.id
                   )) AS exportable
        FROM archive_session s
        LEFT JOIN club c ON c.id = s.club_id
        LEFT JOIN match m ON m.session_id = s.id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    Optional<ArchivePeriod> latestPeriod(ArchiveType type) {
        return jdbc.query("""
                SELECT year, month
                FROM archive_session
                WHERE year IS NOT NULL AND type = :type
                ORDER BY year DESC, month DESC
                LIMIT 1
                """,
            Map.of("type", type.name()),
            (rs, rowNum) -> ArchivePeriod.builder()
                .year(rs.getInt("year"))
                .month(rs.getInt("month"))
                .build()
        ).stream().findFirst();
    }

    boolean existsByName(String name) {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM archive_session
                WHERE name = :name
                """,
            Map.of("name", name),
            Integer.class
        ) > 0;
    }

    UUID upsert(String name, LocalDate matchDate, UUID clubId, boolean tournament, ArchiveType type) {
        return jdbc.queryForObject("""
                INSERT INTO archive_session (name, match_date, club_id, tournament, type)
                VALUES (:name, :matchDate, :clubId, :tournament, :type)
                ON CONFLICT (name) DO UPDATE SET
                    match_date = coalesce(EXCLUDED.match_date, archive_session.match_date),
                    club_id = coalesce(EXCLUDED.club_id, archive_session.club_id),
                    tournament = archive_session.tournament OR EXCLUDED.tournament,
                    type = EXCLUDED.type,
                    indexed_at = now()
                RETURNING id
                """,
            new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("matchDate", matchDate)
                .addValue("clubId", clubId)
                .addValue("tournament", tournament)
                .addValue("type", type.name()),
            UUID.class
        );
    }

    UUID insert(String name, LocalDate matchDate, UUID clubId, boolean tournament, ArchiveType type) {
        return jdbc.queryForObject("""
                INSERT INTO archive_session (name, match_date, club_id, tournament, type)
                VALUES (:name, :matchDate, :clubId, :tournament, :type)
                RETURNING id
                """,
            new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("matchDate", matchDate)
                .addValue("clubId", clubId)
                .addValue("tournament", tournament)
                .addValue("type", type.name()),
            UUID.class
        );
    }

    Optional<ArchiveSession> find(UUID id) {
        List<ArchiveSession> sessions = jdbc.query(
            SESSION_SELECT + """

                WHERE s.id = :id
                GROUP BY s.id, c.id
                """,
            Map.of("id", id),
            SESSION_ROW_MAPPER
        );
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        ArchiveSession session = sessions.get(0);
        Map<UUID, List<ArchiveSessionFile>> files = files(List.of(session.getId()));
        return Optional.of(session.toBuilder().files(files.getOrDefault(session.getId(), List.of())).build());
    }

    List<ArchiveSession> sessions(int year, int month, UUID clubId, UUID playerId, ArchiveType type) {
        StringBuilder sql = new StringBuilder(SESSION_SELECT)
            .append(" WHERE s.year = :year AND s.month = :month AND s.type = :type");
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("year", year)
            .addValue("month", month)
            .addValue("type", type.name());
        if (clubId != null) {
            sql.append(" AND s.club_id = :clubId");
            params.addValue("clubId", clubId);
        }
        if (playerId != null) {
            sql.append("""

                 AND EXISTS (
                    SELECT 1
                    FROM match fm
                    JOIN match_player fmp ON fmp.match_id = fm.id
                    JOIN player fp ON fp.id = fmp.player_id
                    WHERE fm.session_id = s.id
                      AND coalesce(fp.alias_of_player_id, fp.id) = :playerId
                 )
                """);
            params.addValue("playerId", playerId);
        }
        sql.append("""

            GROUP BY s.id, c.id
            ORDER BY s.match_date DESC NULLS LAST, s.name
            """);

        List<ArchiveSession> sessions = jdbc.query(sql.toString(), params, SESSION_ROW_MAPPER);
        if (sessions.isEmpty()) {
            return sessions;
        }
        Map<UUID, List<ArchiveSessionFile>> files = files(
            sessions.stream().map(ArchiveSession::getId).toList()
        );
        return sessions.stream()
            .map(session -> session.toBuilder()
                .files(files.getOrDefault(session.getId(), List.of()))
                .build()
            )
            .toList();
    }

    private Map<UUID, List<ArchiveSessionFile>> files(List<UUID> sessionIds) {
        return jdbc.query("""
                SELECT m.session_id, m.id, m.rel_path, m.size_mbytes, m.duration_seconds,
                       m.validation_error,
                       (SELECT count(*) FROM screenshot sh WHERE sh.match_id = m.id) AS screenshots,
                       array_agg(coalesce(root_player.display_name, root_player.name) ORDER BY mp.position)
                           FILTER (WHERE root_player.id IS NOT NULL) AS players
                FROM match m
                LEFT JOIN match_player mp ON mp.match_id = m.id
                LEFT JOIN player p ON p.id = mp.player_id
                LEFT JOIN player root_player ON root_player.id = coalesce(p.alias_of_player_id, p.id)
                WHERE m.session_id IN (:sessionIds)
                GROUP BY m.id
                ORDER BY m.rel_path
                """,
            Map.of("sessionIds", sessionIds),
            (ResultSetExtractor<Map<UUID, List<ArchiveSessionFile>>>) rs -> {
                Map<UUID, List<ArchiveSessionFile>> result = new LinkedHashMap<>();
                while (rs.next()) {
                    UUID sessionId = UUID.fromString(rs.getString("session_id"));
                    result.computeIfAbsent(sessionId, id -> new ArrayList<>()).add(
                        ArchiveSessionFile.builder()
                            .id(UUID.fromString(rs.getString("id")))
                            .relPath(rs.getString("rel_path"))
                            .players(playerNames(rs))
                            .sizeMbytes(rs.getInt("size_mbytes"))
                            .durationSeconds((Integer) rs.getObject("duration_seconds"))
                            .screenshots(rs.getInt("screenshots"))
                            .validationError(rs.getString("validation_error"))
                            .build()
                    );
                }
                return result;
            }
        );
    }

    private static List<String> playerNames(ResultSet rs) throws SQLException {
        Array array = rs.getArray("players");
        if (array == null) {
            return List.of();
        }
        String[] values = (String[]) array.getArray();
        return values == null ? List.of() : Arrays.asList(values);
    }
}
