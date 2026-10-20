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

    private static final String BRIEF_SELECT = """
        SELECT m.id, m.rel_path, m.year, m.month, m.match_date, m.club_id, m.tournament,
               m.duration_seconds, m.size_mbytes, m.validation_error,
               coalesce(c.display_name, c.name) AS club_name,
               array_agg(coalesce(p.display_name, p.name) ORDER BY mp.position)
                   FILTER (WHERE p.id IS NOT NULL) AS players
        FROM match m
        LEFT JOIN club c ON c.id = m.club_id
        LEFT JOIN match_player mp ON mp.match_id = m.id
        LEFT JOIN player p ON p.id = mp.player_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public UUID upsertMeta(
        String relPath,
        Integer year,
        Integer month,
        LocalDate matchDate,
        UUID clubId,
        boolean tournament,
        int sizeMbytes,
        String validationError
    ) {
        return jdbc.queryForObject("""
                INSERT INTO match (rel_path, year, month, match_date, club_id, tournament, size_mbytes, validation_error)
                VALUES (:relPath, :year, :month, :matchDate, :clubId, :tournament, :sizeMbytes, :validationError)
                ON CONFLICT (rel_path) DO UPDATE SET
                    year = EXCLUDED.year,
                    month = EXCLUDED.month,
                    match_date = EXCLUDED.match_date,
                    club_id = EXCLUDED.club_id,
                    tournament = EXCLUDED.tournament,
                    size_mbytes = EXCLUDED.size_mbytes,
                    validation_error = EXCLUDED.validation_error,
                    indexed_at = now()
                RETURNING id
                """,
            new MapSqlParameterSource()
                .addValue("relPath", relPath)
                .addValue("year", year)
                .addValue("month", month)
                .addValue("matchDate", matchDate)
                .addValue("clubId", clubId)
                .addValue("tournament", tournament)
                .addValue("sizeMbytes", sizeMbytes)
                .addValue("validationError", validationError),
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

    public void updateRelPath(UUID matchId, String relPath) {        jdbc.update("""
                UPDATE match
                SET rel_path = :relPath
                WHERE id = :matchId
                """,
            Map.of("matchId", matchId, "relPath", relPath)
        );
    }

    public Optional<Match> findById(UUID matchId) {
        return jdbc.query("""
                SELECT m.*, coalesce(c.display_name, c.name) AS club_name
                FROM match m
                LEFT JOIN club c ON c.id = m.club_id
                WHERE m.id = :matchId
                """,
            Map.of("matchId", matchId),
            MATCH_ROW_MAPPER
        ).stream().findFirst();
    }

    public List<Match> validWithoutScreenshots(int limit) {
        return jdbc.query("""
                SELECT m.*, coalesce(c.display_name, c.name) AS club_name
                FROM match m
                LEFT JOIN club c ON c.id = m.club_id
                WHERE m.validation_error IS NULL
                ORDER BY m.match_date, m.rel_path
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
        Boolean invalid,
        int limit,
        int offset
    ) {
        StringBuilder sql = new StringBuilder(BRIEF_SELECT).append(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", limit)
            .addValue("offset", offset);
        if (year != null) {
            sql.append(" AND m.year = :year");
            params.addValue("year", year);
        }
        if (month != null) {
            sql.append(" AND m.month = :month");
            params.addValue("month", month);
        }
        if (clubId != null) {
            sql.append(" AND m.club_id = :clubId");
            params.addValue("clubId", clubId);
        }
        if (invalid != null) {
            sql.append(invalid ? " AND m.validation_error IS NOT NULL" : " AND m.validation_error IS NULL");
        }
        sql.append("""

            GROUP BY m.id, c.id
            ORDER BY m.match_date DESC NULLS LAST, m.rel_path
            LIMIT :limit OFFSET :offset
            """);
        return jdbc.query(sql.toString(), params, MATCH_BRIEF_ROW_MAPPER);
    }

    public Optional<MatchBrief> brief(UUID matchId) {
        return jdbc.query(BRIEF_SELECT + """

            WHERE m.id = :matchId
            GROUP BY m.id, c.id
            """,
            Map.of("matchId", matchId),
            MATCH_BRIEF_ROW_MAPPER
        ).stream().findFirst();
    }

    public int countBriefs(Integer year, Integer month, UUID clubId, Boolean invalid) {        StringBuilder sql = new StringBuilder("SELECT count(*) FROM match m WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (year != null) {
            sql.append(" AND m.year = :year");
            params.addValue("year", year);
        }
        if (month != null) {
            sql.append(" AND m.month = :month");
            params.addValue("month", month);
        }
        if (clubId != null) {
            sql.append(" AND m.club_id = :clubId");
            params.addValue("clubId", clubId);
        }
        if (invalid != null) {
            sql.append(invalid ? " AND m.validation_error IS NOT NULL" : " AND m.validation_error IS NULL");
        }
        return jdbc.queryForObject(sql.toString(), params, Integer.class);
    }

    public List<ArchiveYear> tree() {
        return jdbc.query("""
                SELECT year, month,
                       count(*) AS matches,
                       count(*) FILTER (WHERE validation_error IS NOT NULL) AS invalid
                FROM match
                WHERE year IS NOT NULL
                GROUP BY year, month
                ORDER BY year DESC, month DESC
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
