package com.github.bogdanovmn.badmshot.catalog;

import com.github.bogdanovmn.badmshot.common.FullTextQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlayerRepository {

    private static final RowMapper<Player> PLAYER_ROW_MAPPER = (rs, rowNum) -> Player.builder()
        .id(UUID.fromString(rs.getString("id")))
        .name(rs.getString("name"))
        .displayName(rs.getString("display_name"))
        .tournamentAlias(rs.getString("tournament_alias"))
        .build();

    private final NamedParameterJdbcTemplate jdbc;

    public UUID upsert(String name) {
        return jdbc.queryForObject("""
                INSERT INTO player (name)
                VALUES (:name)
                ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """,
            Map.of("name", name),
            UUID.class
        );
    }

    public Optional<Player> findById(UUID playerId) {
        return jdbc.query("""
                SELECT id, name, display_name, tournament_alias
                FROM player
                WHERE id = :playerId
                """,
            Map.of("playerId", playerId),
            PLAYER_ROW_MAPPER
        ).stream().findFirst();
    }

    public List<Player> byIds(List<UUID> ids) {        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT id, name, display_name, tournament_alias
                FROM player
                WHERE id IN (:ids)
                ORDER BY name
                """,
            Map.of("ids", ids),
            PLAYER_ROW_MAPPER
        );
    }

    public List<Player> search(String term, UUID clubId, int limit) {
        Optional<String> query = FullTextQuery.of(term);
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.display_name, p.tournament_alias
                FROM player p
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        query.ifPresent(q -> {
            sql.append(" AND p.search_vector @@ to_tsquery('simple', :tsq)");
            params.addValue("tsq", q);
        });
        if (clubId != null) {
            sql.append("""
                 AND EXISTS (
                    SELECT 1
                    FROM match_player mp
                    JOIN match m ON m.id = mp.match_id
                    WHERE mp.player_id = p.id AND m.club_id = :clubId
                 )
                """);
            params.addValue("clubId", clubId);
        }
        sql.append(" ORDER BY coalesce(p.display_name, p.name) LIMIT :limit");
        return jdbc.query(sql.toString(), params, PLAYER_ROW_MAPPER);
    }

    public List<Player> byMatch(UUID matchId) {
        return jdbc.query("""
                SELECT p.id, p.name, p.display_name, p.tournament_alias
                FROM match_player mp
                JOIN player p ON p.id = mp.player_id
                WHERE mp.match_id = :matchId
                ORDER BY mp.position
                """,
            Map.of("matchId", matchId),
            PLAYER_ROW_MAPPER
        );
    }

    public List<CatalogTag> tags() {
        return jdbc.query("""
                SELECT p.id,
                       coalesce(nullif(p.display_name, ''), p.name) AS label,
                       count(DISTINCT mp.match_id) AS matches
                FROM player p
                JOIN match_player mp ON mp.player_id = p.id
                GROUP BY p.id, p.name, p.display_name
                ORDER BY matches DESC, label
                """,
            (rs, rowNum) -> CatalogTag.builder()
                .id(UUID.fromString(rs.getString("id")))
                .label(rs.getString("label"))
                .matches(rs.getLong("matches"))
                .build()
        );
    }

    public void update(UUID playerId, String displayName, String tournamentAlias) {
        jdbc.update("""
                UPDATE player
                SET display_name = :displayName,
                    tournament_alias = :tournamentAlias
                WHERE id = :playerId
                """,
            new MapSqlParameterSource()
                .addValue("playerId", playerId)
                .addValue("displayName", displayName)
                .addValue("tournamentAlias", tournamentAlias)
        );
    }
}
