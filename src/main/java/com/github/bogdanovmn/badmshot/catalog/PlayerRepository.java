package com.github.bogdanovmn.badmshot.catalog;

import com.github.bogdanovmn.badmshot.archive.ArchiveType;
import com.github.bogdanovmn.badmshot.common.FullTextQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
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
        .aliasOfPlayerId(rs.getString("alias_of_player_id") == null
            ? null
            : UUID.fromString(rs.getString("alias_of_player_id")))
        .alias(rs.getString("alias_id") == null
            ? null
            : PlayerAlias.builder()
                .id(UUID.fromString(rs.getString("alias_id")))
                .name(rs.getString("alias_name"))
                .displayName(rs.getString("alias_display_name"))
                .build())
        .build();

    private static final String PLAYER_SELECT = """
        SELECT p.id, p.name, p.display_name, p.alias_of_player_id,
               a.id AS alias_id, a.name AS alias_name, a.display_name AS alias_display_name
        FROM player p
        LEFT JOIN player a ON a.alias_of_player_id = p.id
        """;

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
        return jdbc.query(PLAYER_SELECT + """
                WHERE p.id = :playerId
                """,
            Map.of("playerId", playerId),
            PLAYER_ROW_MAPPER
        ).stream().findFirst();
    }

    public Optional<UUID> canonicalId(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return jdbc.queryForList("""
                SELECT coalesce(alias_of_player_id, id)
                FROM player
                WHERE id = :playerId
                """,
            Map.of("playerId", playerId),
            UUID.class
        ).stream().findFirst();
    }

    public List<UUID> canonicalIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT DISTINCT coalesce(alias_of_player_id, id)
                FROM player
                WHERE id IN (:ids)
                """,
            Map.of("ids", ids),
            UUID.class
        );
    }

    public List<Player> byIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(PLAYER_SELECT + """
                WHERE p.alias_of_player_id IS NULL
                  AND lower(p.name) <> 'skip'
                  AND p.id IN (
                    SELECT coalesce(pp.alias_of_player_id, pp.id)
                    FROM player pp
                    WHERE pp.id IN (:ids)
                  )
                ORDER BY coalesce(nullif(p.display_name, ''), p.name)
                """,
            Map.of("ids", ids),
            PLAYER_ROW_MAPPER
        );
    }

    public List<Player> search(String term, UUID clubId, int limit) {
        String preparedTerm = FullTextQuery.of(term).orElse("");
        String searchTerm = FullTextQuery.normalized(term);
        StringBuilder sql = new StringBuilder(PLAYER_SELECT).append("""
                WHERE p.alias_of_player_id IS NULL
                  AND lower(p.name) <> 'skip'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("term", searchTerm)
            .addValue("limit", limit);
        List<String> termConditions = new ArrayList<>();
        if (!preparedTerm.isEmpty()) {
            termConditions.add("""
                (
                   p.search_vector @@ to_tsquery('simple', :tsq)
                   OR EXISTS (
                       SELECT 1
                       FROM player ax
                       WHERE ax.alias_of_player_id = p.id
                         AND ax.search_vector @@ to_tsquery('simple', :tsq)
                   )
                )
                """);
            params.addValue("tsq", preparedTerm);
        }
        if (!searchTerm.isEmpty()) {
            termConditions.add("""
                LEAST(
                   LOWER(coalesce(nullif(p.display_name, ''), p.name)) <-> LOWER(:term),
                   LOWER(coalesce(nullif(a.display_name, ''), a.name, '')) <-> LOWER(:term)
                ) <= :maxDistance
                """);
            params.addValue("maxDistance", maxDistance(searchTerm));
        }
        if (!termConditions.isEmpty()) {
            sql.append(" AND (").append(String.join(" OR ", termConditions)).append(")");
        }
        if (clubId != null) {
            sql.append("""
                 AND EXISTS (
                    SELECT 1
                     FROM match_player mp
                     JOIN match m ON m.id = mp.match_id
                     JOIN archive_session s ON s.id = m.session_id
                     WHERE s.club_id = :clubId
                      AND (mp.player_id = p.id OR mp.player_id = a.id)
                 )
                """);
            params.addValue("clubId", clubId);
        }
        sql.append("""
             ORDER BY LEAST(
                 LOWER(coalesce(nullif(p.display_name, ''), p.name)) <-> LOWER(:term),
                 LOWER(coalesce(nullif(a.display_name, ''), a.name, '')) <-> LOWER(:term)
             ), coalesce(nullif(p.display_name, ''), p.name)
             LIMIT :limit
            """);
        return jdbc.query(sql.toString(), params, PLAYER_ROW_MAPPER);
    }

    private static double maxDistance(String term) {
        int length = term.codePointCount(0, term.length());
        if (length <= 3) {
            return 0.82;
        }
        return 0.85;
    }

    public List<Player> byMatch(UUID matchId) {
        return jdbc.query(PLAYER_SELECT + """
                JOIN match_player mp ON mp.player_id = p.id
                WHERE mp.match_id = :matchId
                ORDER BY mp.position
                """,
            Map.of("matchId", matchId),
            PLAYER_ROW_MAPPER
        );
    }

    public List<CatalogTag> tags(ArchiveType type) {
        return jdbc.query("""
                SELECT p.id,
                       coalesce(nullif(p.display_name, ''), p.name) AS label,
                       count(*) AS matches
                FROM (
                    SELECT DISTINCT coalesce(x.alias_of_player_id, x.id) AS root_id, mp.match_id
                    FROM match_player mp
                    JOIN player x ON x.id = mp.player_id
                    JOIN match m ON m.id = mp.match_id
                    WHERE m.type = :type
                ) t
                JOIN player p ON p.id = t.root_id
                WHERE lower(p.name) <> 'skip'
                GROUP BY p.id, p.name, p.display_name
                ORDER BY label
                """,
            Map.of("type", type.name()),
            (rs, rowNum) -> CatalogTag.builder()
                .id(UUID.fromString(rs.getString("id")))
                .label(rs.getString("label"))
                .matches(rs.getLong("matches"))
                .build()
        );
    }

    public void updateDisplayName(UUID playerId, String displayName) {
        jdbc.update("""
                UPDATE player
                SET display_name = :displayName
                WHERE id = :playerId
                """,
            new MapSqlParameterSource()
                .addValue("playerId", playerId)
                .addValue("displayName", displayName)
        );
    }

    public void clearAlias(UUID playerId) {
        jdbc.update("""
                UPDATE player
                SET alias_of_player_id = NULL
                WHERE alias_of_player_id = :playerId
                """,
            Map.of("playerId", playerId)
        );
    }

    public void setAlias(UUID playerId, UUID aliasPlayerId) {
        jdbc.update("""
                UPDATE player
                SET alias_of_player_id = :playerId
                WHERE id = :aliasPlayerId
                """,
            Map.of("playerId", playerId, "aliasPlayerId", aliasPlayerId)
        );
    }
}
