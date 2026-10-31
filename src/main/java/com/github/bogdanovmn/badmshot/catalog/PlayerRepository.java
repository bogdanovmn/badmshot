package com.github.bogdanovmn.badmshot.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.bogdanovmn.badmshot.archive.ArchiveType;
import com.github.bogdanovmn.badmshot.common.FullTextQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
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

    private static final String UNPROCESSED_CONDITION = """
          AND (NOT :unprocessed OR (
              nullif(p.display_name, '') IS NULL
              AND p.alias_of_player_id IS NULL
          ))
        """;

    private final NamedParameterJdbcTemplate jdbc;

    private final Cache<String, List<PlayerLetterCount>> lettersCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(2)
        .build();

    public UUID upsert(String name) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO player (name)
                VALUES (:name)
                ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """,
            Map.of("name", name),
            UUID.class
        );
        invalidateLetters();
        return id;
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
        return search(term, clubId, limit, List.of(), false, false, false);
    }

    /**
     * Поиск игроков для формы фильтра.
     * <p>
     * {@code availableOnly} оставляет только игроков, у которых есть кадры подходящего
     * типа. В режиме {@code together} дополнительно требуются общие кадры со всеми
     * уже выбранными игроками — так нельзя собрать заведомо пустую комбинацию.
     */
    public List<Player> search(
        String term,
        UUID clubId,
        int limit,
        List<UUID> playerIds,
        boolean together,
        boolean includeIndi,
        boolean availableOnly
    ) {
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
                   OR a.search_vector @@ to_tsquery('simple', :tsq)
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
        boolean hasSelectedPlayers = playerIds != null && !playerIds.isEmpty();
        if (availableOnly || (together && hasSelectedPlayers)) {
            params.addValue("gamesType", ArchiveType.GAMES.name())
                .addValue("includeIndi", includeIndi);
        }
        if (together && hasSelectedPlayers) {
            sql.append("""
                 AND p.id IN (
                    SELECT DISTINCT coalesce(participant.alias_of_player_id, participant.id)
                    FROM match_player participant_mp
                    JOIN player participant ON participant.id = participant_mp.player_id
                    JOIN match participant_match ON participant_match.id = participant_mp.match_id
                    WHERE (participant_match.type = :gamesType OR :includeIndi)
                      AND EXISTS (
                          SELECT 1 FROM screenshot sc
                          WHERE sc.match_id = participant_match.id
                      )
                      AND participant_match.id IN (
                          SELECT selected_mp.match_id
                          FROM match_player selected_mp
                          JOIN player selected_player ON selected_player.id = selected_mp.player_id
                          WHERE coalesce(selected_player.alias_of_player_id, selected_player.id) IN (:playerIds)
                          GROUP BY selected_mp.match_id
                          HAVING count(DISTINCT coalesce(
                              selected_player.alias_of_player_id, selected_player.id
                          )) = :playerIdsCount
                      )
                 )
                """);
            params.addValue("playerIdsCount", playerIds.size())
                .addValue("playerIds", playerIds);
        } else if (availableOnly) {
            sql.append("""
                 AND p.id IN (
                    SELECT DISTINCT coalesce(participant.alias_of_player_id, participant.id)
                    FROM match_player participant_mp
                    JOIN player participant ON participant.id = participant_mp.player_id
                    JOIN match participant_match ON participant_match.id = participant_mp.match_id
                    WHERE (participant_match.type = :gamesType OR :includeIndi)
                      AND EXISTS (
                          SELECT 1 FROM screenshot sc
                          WHERE sc.match_id = participant_match.id
                      )
                 )
                """);
        }
        if (searchTerm.isEmpty()) {
            sql.append("""
                 ORDER BY coalesce(nullif(p.display_name, ''), p.name)
                 LIMIT :limit
                """);
        } else {
            sql.append("""
                 ORDER BY LEAST(
                     LOWER(coalesce(nullif(p.display_name, ''), p.name)) <-> LOWER(:term),
                     LOWER(coalesce(nullif(a.display_name, ''), a.name, '')) <-> LOWER(:term)
                 ), coalesce(nullif(p.display_name, ''), p.name)
                 LIMIT :limit
                """);
        }
        return jdbc.query(sql.toString(), params, PLAYER_ROW_MAPPER);
    }

    private static double maxDistance(String term) {
        int length = term.codePointCount(0, term.length());
        if (length <= 3) {
            return 0.82;
        }
        return 0.85;
    }

    public List<Player> byLetter(String letter, boolean unprocessedOnly) {
        return jdbc.query(PLAYER_SELECT + """
                WHERE p.alias_of_player_id IS NULL
                  AND lower(p.name) <> 'skip'
                  AND upper(left(p.name, 1)) = upper(:letter)
                  AND p.id IN (
                      SELECT coalesce(pp.alias_of_player_id, pp.id)
                      FROM match_player mp
                      JOIN player pp ON pp.id = mp.player_id
                  )
                """ + UNPROCESSED_CONDITION + """
                ORDER BY lower(p.name)
                """,
            new MapSqlParameterSource()
                .addValue("letter", letter)
                .addValue("unprocessed", unprocessedOnly),
            PLAYER_ROW_MAPPER
        );
    }

    public List<Player> withoutMatches(boolean unprocessedOnly) {
        return jdbc.query(PLAYER_SELECT + """
                WHERE p.alias_of_player_id IS NULL
                  AND lower(p.name) <> 'skip'
                  AND p.id NOT IN (
                      SELECT coalesce(pp.alias_of_player_id, pp.id)
                      FROM match_player mp
                      JOIN player pp ON pp.id = mp.player_id
                  )
                """ + UNPROCESSED_CONDITION + """
                ORDER BY lower(coalesce(nullif(p.display_name, ''), p.name))
                """,
            new MapSqlParameterSource()
                .addValue("unprocessed", unprocessedOnly),
            PLAYER_ROW_MAPPER
        );
    }

    /**
     * Есть ли у игроков кадры индивидуальных тренировок.
     * В режиме «вместе» требуется тренировка, где присутствуют все игроки.
     */
    public boolean indiAvailable(List<UUID> playerIds, boolean together) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("indiType", ArchiveType.INDI.name());
        if (playerIds == null || playerIds.isEmpty()) {
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM match m
                        WHERE m.type = :indiType
                          AND EXISTS (SELECT 1 FROM screenshot s WHERE s.match_id = m.id)
                    )
                    """,
                params,
                Boolean.class
            ));
        }
        params.addValue("playerIds", playerIds);
        StringBuilder sql = new StringBuilder("""
            SELECT EXISTS (
                SELECT 1
                FROM match m
                JOIN match_player mp ON mp.match_id = m.id
                JOIN player p ON p.id = mp.player_id
                WHERE m.type = :indiType
                  AND coalesce(p.alias_of_player_id, p.id) IN (:playerIds)
                  AND EXISTS (SELECT 1 FROM screenshot s WHERE s.match_id = m.id)
            """);
        if (together) {
            sql.append("""
                  GROUP BY m.id
                  HAVING count(DISTINCT coalesce(p.alias_of_player_id, p.id)) = :playerIdsCount
                """);
            params.addValue("playerIdsCount", playerIds.size());
        }
        sql.append(")");
        return Boolean.TRUE.equals(jdbc.queryForObject(sql.toString(), params, Boolean.class));
    }

    /**
     * Есть ли кадры хотя бы у одного из выбранных игроков.
     * Используется в режиме «любой из игроков».
     */
    public boolean anyFramesAvailable(List<UUID> playerIds, boolean includeIndi) {
        if (playerIds == null || playerIds.isEmpty()) {
            return true;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM match m
                    JOIN match_player mp ON mp.match_id = m.id
                    JOIN player p ON p.id = mp.player_id
                    WHERE (m.type = :gamesType OR :includeIndi)
                      AND coalesce(p.alias_of_player_id, p.id) IN (:playerIds)
                      AND EXISTS (SELECT 1 FROM screenshot s WHERE s.match_id = m.id)
                )
                """,
            new MapSqlParameterSource()
                .addValue("gamesType", ArchiveType.GAMES.name())
                .addValue("includeIndi", includeIndi)
                .addValue("playerIds", playerIds),
            Boolean.class
        ));
    }

    /**
     * Есть ли кадр, в котором присутствуют все выбранные игроки одновременно.
     * Используется, чтобы не дать включить режим «вместе» для несовместимого набора.
     */
    public boolean jointFramesAvailable(List<UUID> playerIds, boolean includeIndi) {
        if (playerIds == null || playerIds.isEmpty()) {
            return true;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM match m
                    JOIN match_player mp ON mp.match_id = m.id
                    JOIN player p ON p.id = mp.player_id
                    WHERE (m.type = :gamesType OR :includeIndi)
                      AND coalesce(p.alias_of_player_id, p.id) IN (:playerIds)
                      AND EXISTS (SELECT 1 FROM screenshot s WHERE s.match_id = m.id)
                    GROUP BY m.id
                    HAVING count(DISTINCT coalesce(p.alias_of_player_id, p.id)) = :playerIdsCount
                )
                """,
            new MapSqlParameterSource()
                .addValue("gamesType", ArchiveType.GAMES.name())
                .addValue("includeIndi", includeIndi)
                .addValue("playerIds", playerIds)
                .addValue("playerIdsCount", playerIds.size()),
            Boolean.class
        ));
    }

    public List<PlayerLetterCount> letters(boolean unprocessedOnly) {
        return lettersCache.get("letters:" + unprocessedOnly, key -> jdbc.query("""
                SELECT upper(left(p.name, 1)) AS letter, count(*) AS players
                FROM player p
                WHERE p.alias_of_player_id IS NULL
                  AND lower(p.name) <> 'skip'
                  AND p.id IN (
                      SELECT coalesce(pp.alias_of_player_id, pp.id)
                      FROM match_player mp
                      JOIN player pp ON pp.id = mp.player_id
                  )
                """ + UNPROCESSED_CONDITION + """
                GROUP BY letter
                ORDER BY letter
                """,
            new MapSqlParameterSource()
                .addValue("unprocessed", unprocessedOnly),
            (rs, rowNum) -> new PlayerLetterCount(
                rs.getString("letter"),
                rs.getLong("players")
            )
        ));
    }

    private void invalidateLetters() {
        lettersCache.invalidateAll();
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

    public List<CatalogTag> tags(ArchiveType type, boolean withDisplayNameOnly) {
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
                  AND (NOT :withDisplayNameOnly OR nullif(p.display_name, '') IS NOT NULL)
                GROUP BY p.id, p.name, p.display_name
                ORDER BY label
                """,
            new MapSqlParameterSource()
                .addValue("type", type.name())
                .addValue("withDisplayNameOnly", withDisplayNameOnly),
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
        invalidateLetters();
    }

    public Optional<UUID> findIdByName(String name, UUID excludePlayerId) {
        return jdbc.queryForList("""
                SELECT id
                FROM player
                WHERE name = :name AND id <> :excludePlayerId
                """,
            new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("excludePlayerId", excludePlayerId),
            UUID.class
        ).stream().findFirst();
    }

    public void updateName(UUID playerId, String name) {
        jdbc.update("""
                UPDATE player
                SET name = :name
                WHERE id = :playerId
                """,
            new MapSqlParameterSource()
                .addValue("playerId", playerId)
                .addValue("name", name)
        );
        invalidateLetters();
    }

    public void mergeInto(UUID playerId, UUID targetPlayerId) {
        jdbc.update("""
                UPDATE player
                SET alias_of_player_id = :targetPlayerId
                WHERE alias_of_player_id = :playerId
                  AND id <> :targetPlayerId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM player p
                      WHERE p.alias_of_player_id = :targetPlayerId
                        AND p.id <> :playerId
                  )
                """,
            Map.of("playerId", playerId, "targetPlayerId", targetPlayerId)
        );
        jdbc.update("""
                DELETE FROM player
                WHERE id = :playerId
                """,
            Map.of("playerId", playerId)
        );
        invalidateLetters();
    }

    public void clearAlias(UUID playerId) {
        jdbc.update("""
                UPDATE player
                SET alias_of_player_id = NULL
                WHERE alias_of_player_id = :playerId
                """,
            Map.of("playerId", playerId)
        );
        invalidateLetters();
    }

    public void setAlias(UUID playerId, UUID aliasPlayerId) {
        jdbc.update("""
                UPDATE player
                SET alias_of_player_id = :playerId
                WHERE id = :aliasPlayerId
                """,
            Map.of("playerId", playerId, "aliasPlayerId", aliasPlayerId)
        );
        invalidateLetters();
    }
}
