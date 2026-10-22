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
class MatchRepository {

    private static final RowMapper<Match> MATCH_ROW_MAPPER = (rs, rowNum) -> Match.builder()
        .id(UUID.fromString(rs.getString("id")))
        .relPath(rs.getString("rel_path"))
        .year((Integer) rs.getObject("year"))
        .month((Integer) rs.getObject("month"))
        .matchDate(rs.getObject("match_date", LocalDate.class))
        .clubId(rs.getString("club_id") == null ? null : UUID.fromString(rs.getString("club_id")))
        .clubName(rs.getString("club_name"))
        .tournament(rs.getBoolean("tournament"))
        .durationSeconds((Integer) rs.getObject("duration_seconds"))
        .sizeMbytes(rs.getInt("size_mbytes"))
        .validationError(rs.getString("validation_error"))
        .indexedAt(rs.getTimestamp("indexed_at").toInstant())
        .build();

    private static final RowMapper<MatchBrief> MATCH_BRIEF_ROW_MAPPER = (rs, rowNum) -> MatchBrief.builder()
        .id(UUID.fromString(rs.getString("id")))
        .relPath(rs.getString("rel_path"))
        .year((Integer) rs.getObject("year"))
        .month((Integer) rs.getObject("month"))
        .matchDate(rs.getObject("match_date", LocalDate.class))
        .clubId(rs.getString("club_id") == null ? null : UUID.fromString(rs.getString("club_id")))
        .clubName(rs.getString("club_name"))
        .tournament(rs.getBoolean("tournament"))
        .durationSeconds((Integer) rs.getObject("duration_seconds"))
        .sizeMbytes(rs.getInt("size_mbytes"))
        .validationError(rs.getString("validation_error"))
        .players(playerNames(rs))
        .build();

    private static final String MATCH_SELECT = """
        SELECT m.id, m.rel_path, s.year, s.month, s.match_date, s.club_id, s.tournament,
               m.duration_seconds, m.size_mbytes, m.validation_error, m.indexed_at,
               coalesce(c.display_name, c.name) AS club_name
        FROM match m
        LEFT JOIN archive_session s ON s.id = m.session_id
        LEFT JOIN club c ON c.id = s.club_id
        """;

    private static final String BRIEF_SELECT = """
        SELECT m.id, m.rel_path, s.year, s.month, s.match_date, s.club_id, s.tournament,
               m.duration_seconds, m.size_mbytes, m.validation_error,
               coalesce(c.display_name, c.name) AS club_name,
               array_agg(coalesce(root_player.display_name, root_player.name) ORDER BY mp.position)
                   FILTER (WHERE root_player.id IS NOT NULL) AS players
        FROM match m
        LEFT JOIN archive_session s ON s.id = m.session_id
        LEFT JOIN club c ON c.id = s.club_id
        LEFT JOIN match_player mp ON mp.match_id = m.id
        LEFT JOIN player p ON p.id = mp.player_id
        LEFT JOIN player root_player ON root_player.id = coalesce(p.alias_of_player_id, p.id)
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public UUID upsertMeta(String relPath, UUID sessionId, int sizeMbytes, String validationError) {
        return jdbc.queryForObject("""
                INSERT INTO match (rel_path, session_id, size_mbytes, validation_error)
                VALUES (:relPath, :sessionId, :sizeMbytes, :validationError)
                ON CONFLICT (rel_path) DO UPDATE SET
                    session_id = EXCLUDED.session_id,
                    size_mbytes = EXCLUDED.size_mbytes,
                    validation_error = EXCLUDED.validation_error,
                    indexed_at = now()
                RETURNING id
                """,
            new MapSqlParameterSource()
                .addValue("relPath", relPath)
                .addValue("sessionId", sessionId)
                .addValue("sizeMbytes", sizeMbytes)
                .addValue("validationError", validationError),
            UUID.class
        );
    }

    public UUID insertImported(
        UUID sessionId,
        String relPath,
        Integer durationSeconds,
        int sizeMbytes
    ) {
        return jdbc.queryForObject("""
                INSERT INTO match (session_id, rel_path, duration_seconds, size_mbytes)
                VALUES (:sessionId, :relPath, :durationSeconds, :sizeMbytes)
                RETURNING id
                """,
            new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("relPath", relPath)
                .addValue("durationSeconds", durationSeconds)
                .addValue("sizeMbytes", sizeMbytes),
            UUID.class
        );
    }

    public void replacePlayers(UUID matchId, List<UUID> playerIds) {
        jdbc.update("""
                DELETE FROM match_player WHERE match_id = :matchId
                """,
            Map.of("matchId", matchId)
        );
        for (int position = 0; position < playerIds.size(); position++) {
            jdbc.update("""
                    INSERT INTO match_player (match_id, player_id, position)
                    VALUES (:matchId, :playerId, :position)
                    """,
                Map.of(
                    "matchId", matchId,
                    "playerId", playerIds.get(position),
                    "position", position
                )
            );
        }
    }

    public void updateDuration(UUID matchId, int durationSeconds) {
        jdbc.update("""
                UPDATE match
                SET duration_seconds = :durationSeconds
                WHERE id = :matchId
                """,
            Map.of("matchId", matchId, "durationSeconds", durationSeconds)
        );
    }

    public boolean existsByRelPath(String relPath) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM match WHERE rel_path = :relPath
                """,
            Map.of("relPath", relPath),
            Integer.class
        ) > 0;
    }

    public void updateRelPath(UUID matchId, String relPath) {
        jdbc.update("""
                UPDATE match
                SET rel_path = :relPath
                WHERE id = :matchId
                """,
            Map.of("matchId", matchId, "relPath", relPath)
        );
    }

    public Optional<Match> findById(UUID matchId) {
        return jdbc.query(
            MATCH_SELECT + """

                WHERE m.id = :matchId
                """,
            Map.of("matchId", matchId),
            MATCH_ROW_MAPPER
        ).stream().findFirst();
    }

    public List<Match> validWithoutScreenshots(int limit) {
        return jdbc.query(
            MATCH_SELECT + """

                WHERE m.validation_error IS NULL
                ORDER BY s.match_date, m.rel_path
                LIMIT :limit
                """,
            Map.of("limit", limit),
            MATCH_ROW_MAPPER
        );
    }

    public List<MatchBrief> briefs(
        Integer year,
        Integer month,
        UUID clubId,
        UUID playerId,
        Boolean invalid,
        int limit,
        int offset
    ) {
        StringBuilder sql = new StringBuilder(BRIEF_SELECT).append(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", limit)
            .addValue("offset", offset);
        if (year != null) {
            sql.append(" AND s.year = :year");
            params.addValue("year", year);
        }
        if (month != null) {
            sql.append(" AND s.month = :month");
            params.addValue("month", month);
        }
        if (clubId != null) {
            sql.append(" AND s.club_id = :clubId");
            params.addValue("clubId", clubId);
        }
        if (playerId != null) {
            sql.append("""
                 AND EXISTS (
                    SELECT 1
                    FROM match_player mp2
                    JOIN player p2 ON p2.id = mp2.player_id
                    WHERE mp2.match_id = m.id
                      AND coalesce(p2.alias_of_player_id, p2.id) = :playerId
                 )
                """);
            params.addValue("playerId", playerId);
        }
        if (invalid != null) {
            sql.append(invalid ? " AND m.validation_error IS NOT NULL" : " AND m.validation_error IS NULL");
        }
        sql.append("""

            GROUP BY m.id, s.id, c.id
            ORDER BY s.match_date DESC NULLS LAST, m.rel_path
            LIMIT :limit OFFSET :offset
            """);
        return jdbc.query(sql.toString(), params, MATCH_BRIEF_ROW_MAPPER);
    }

    public Optional<MatchBrief> brief(UUID matchId) {
        return jdbc.query(
            BRIEF_SELECT + """

                WHERE m.id = :matchId
                GROUP BY m.id, s.id, c.id
                """,
            Map.of("matchId", matchId),
            MATCH_BRIEF_ROW_MAPPER
        ).stream().findFirst();
    }

    public int countBriefs(Integer year, Integer month, UUID clubId, UUID playerId, Boolean invalid) {
        StringBuilder sql = new StringBuilder("""
            SELECT count(*)
            FROM match m
            LEFT JOIN archive_session s ON s.id = m.session_id
            WHERE 1 = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (year != null) {
            sql.append(" AND s.year = :year");
            params.addValue("year", year);
        }
        if (month != null) {
            sql.append(" AND s.month = :month");
            params.addValue("month", month);
        }
        if (clubId != null) {
            sql.append(" AND s.club_id = :clubId");
            params.addValue("clubId", clubId);
        }
        if (playerId != null) {
            sql.append("""
                 AND EXISTS (
                    SELECT 1
                    FROM match_player mp2
                    JOIN player p2 ON p2.id = mp2.player_id
                    WHERE mp2.match_id = m.id
                      AND coalesce(p2.alias_of_player_id, p2.id) = :playerId
                 )
                """);
            params.addValue("playerId", playerId);
        }
        if (invalid != null) {
            sql.append(invalid ? " AND m.validation_error IS NOT NULL" : " AND m.validation_error IS NULL");
        }
        return jdbc.queryForObject(sql.toString(), params, Integer.class);
    }

    public List<ArchiveYear> tree() {
        return jdbc.query("""
                SELECT s.year, s.month,
                       count(m.id) AS matches,
                       count(*) FILTER (WHERE m.validation_error IS NOT NULL) AS invalid
                FROM archive_session s
                JOIN match m ON m.session_id = s.id
                WHERE s.year IS NOT NULL
                GROUP BY s.year, s.month
                ORDER BY s.year DESC, s.month DESC
                """,
            Map.of(),
            (ResultSetExtractor<List<ArchiveYear>>) rs -> {
                LinkedHashMap<Integer, List<ArchiveMonth>> byYear = new LinkedHashMap<>();
                while (rs.next()) {
                    int year = rs.getInt("year");
                    byYear.computeIfAbsent(year, y -> new ArrayList<>()).add(
                        ArchiveMonth.builder()
                            .month(rs.getInt("month"))
                            .matches(rs.getInt("matches"))
                            .invalid(rs.getInt("invalid"))
                            .build()
                    );
                }
                return byYear.entrySet().stream()
                    .map(entry -> ArchiveYear.builder()
                        .year(entry.getKey())
                        .matches(entry.getValue().stream().mapToInt(ArchiveMonth::getMatches).sum())
                        .invalid(entry.getValue().stream().mapToInt(ArchiveMonth::getInvalid).sum())
                        .months(entry.getValue())
                        .build()
                    )
                    .toList();
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
