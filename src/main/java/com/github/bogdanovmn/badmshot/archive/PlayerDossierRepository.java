package com.github.bogdanovmn.badmshot.archive;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class PlayerDossierRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PlayerDossierStats stats(UUID playerId) {
        return jdbc.queryForObject("""
                WITH player_matches AS (
                    SELECT DISTINCT m.id, m.type, m.size_mbytes, m.duration_seconds
                    FROM match m
                    JOIN match_player mp ON mp.match_id = m.id
                    JOIN player p ON p.id = mp.player_id
                    WHERE m.validation_error IS NULL
                      AND coalesce(p.alias_of_player_id, p.id) = :playerId
                )
                SELECT count(*) FILTER (WHERE type = :gamesType) AS games,
                       count(*) FILTER (WHERE type = :indiType) AS trainings,
                       coalesce(sum(size_mbytes), 0) AS size_mbytes,
                       coalesce(sum(coalesce(duration_seconds, 0)), 0) AS duration_seconds,
                       (
                           SELECT count(*)
                           FROM screenshot s
                           WHERE s.match_id IN (SELECT id FROM player_matches)
                       ) AS screenshot_count
                FROM player_matches
                """,
            params(playerId),
            (rs, rowNum) -> PlayerDossierStats.builder()
                .games(rs.getInt("games"))
                .trainings(rs.getInt("trainings"))
                .sizeMbytes(rs.getLong("size_mbytes"))
                .durationSeconds(rs.getLong("duration_seconds"))
                .screenshotCount(rs.getLong("screenshot_count"))
                .build()
        );
    }

    public List<PlayerClubStat> clubs(UUID playerId) {
        Map<UUID, ClubAccumulator> byId = new LinkedHashMap<>();
        jdbc.query("""
                WITH player_matches AS (
                    SELECT DISTINCT m.id, m.size_mbytes, m.duration_seconds, s.year,
                           c.id AS club_id,
                           coalesce(nullif(c.display_name, ''), c.name) AS club_name
                    FROM match m
                    JOIN archive_session s ON s.id = m.session_id
                    JOIN club c ON c.id = s.club_id
                    JOIN match_player mp ON mp.match_id = m.id
                    JOIN player p ON p.id = mp.player_id
                    WHERE m.validation_error IS NULL
                      AND m.type = :gamesType
                      AND coalesce(p.alias_of_player_id, p.id) = :playerId
                )
                SELECT club_id, club_name, year,
                       count(*) AS matches,
                       coalesce(sum(size_mbytes), 0) AS size_mbytes,
                       coalesce(sum(coalesce(duration_seconds, 0)), 0) AS duration_seconds
                FROM player_matches
                WHERE year IS NOT NULL
                GROUP BY club_id, club_name, year
                ORDER BY club_name, year DESC
                """,
            params(playerId),
            rs -> {
                UUID clubId = UUID.fromString(rs.getString("club_id"));
                String clubName = rs.getString("club_name");
                ClubAccumulator club = byId.computeIfAbsent(
                    clubId,
                    id -> new ClubAccumulator(id, clubName)
                );
                club.years.add(PlayerClubYearStat.builder()
                    .year(rs.getInt("year"))
                    .matches(rs.getInt("matches"))
                    .sizeMbytes(rs.getLong("size_mbytes"))
                    .durationSeconds(rs.getLong("duration_seconds"))
                    .build()
                );
            }
        );
        return byId.values().stream()
            .map(ClubAccumulator::toStat)
            .sorted(Comparator.comparingInt(PlayerClubStat::getMatches).reversed()
                .thenComparing(PlayerClubStat::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public List<PlayerRelationStat> partners(UUID playerId) {
        return relations(playerId, """
                AND target.players_count = 4
                AND (other.position / 2) = (target.target_position / 2)
                """);
    }

    public List<PlayerRelationStat> opponents(UUID playerId) {
        return relations(playerId, """
                AND (
                    target.players_count = 2
                    OR (
                        target.players_count = 4
                        AND (other.position / 2) <> (target.target_position / 2)
                    )
                )
                """);
    }

    private List<PlayerRelationStat> relations(UUID playerId, String relationCondition) {
        return jdbc.query("""
                WITH target AS (
                    SELECT m.id AS match_id,
                           mp.position AS target_position,
                           (
                               SELECT count(*)
                               FROM match_player all_mp
                               WHERE all_mp.match_id = m.id
                           ) AS players_count
                    FROM match m
                    JOIN match_player mp ON mp.match_id = m.id
                    JOIN player p ON p.id = mp.player_id
                    WHERE m.validation_error IS NULL
                      AND m.type = :gamesType
                      AND coalesce(p.alias_of_player_id, p.id) = :playerId
                )
                SELECT root.id,
                       coalesce(nullif(root.display_name, ''), root.name) AS label,
                       count(DISTINCT target.match_id) AS matches
                FROM target
                JOIN match_player other ON other.match_id = target.match_id
                JOIN player op ON op.id = other.player_id
                JOIN player root ON root.id = coalesce(op.alias_of_player_id, op.id)
                WHERE other.position <> target.target_position
                """ + relationCondition + """
                  AND root.id <> :playerId
                GROUP BY root.id, root.name, root.display_name
                ORDER BY matches DESC, label
                """,
            params(playerId),
            (rs, rowNum) -> PlayerRelationStat.builder()
                .id(UUID.fromString(rs.getString("id")))
                .label(rs.getString("label"))
                .matches(rs.getLong("matches"))
                .build()
        );
    }

    private MapSqlParameterSource params(UUID playerId) {
        return new MapSqlParameterSource()
            .addValue("playerId", playerId)
            .addValue("gamesType", ArchiveType.GAMES.name())
            .addValue("indiType", ArchiveType.INDI.name());
    }

    private static final class ClubAccumulator {
        private final UUID id;
        private final String name;
        private final List<PlayerClubYearStat> years = new ArrayList<>();

        private ClubAccumulator(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        private PlayerClubStat toStat() {
            return PlayerClubStat.builder()
                .id(id)
                .name(name)
                .matches(years.stream().mapToInt(PlayerClubYearStat::getMatches).sum())
                .sizeMbytes(years.stream().mapToLong(PlayerClubYearStat::getSizeMbytes).sum())
                .durationSeconds(years.stream().mapToLong(PlayerClubYearStat::getDurationSeconds).sum())
                .years(years)
                .build();
        }
    }
}
