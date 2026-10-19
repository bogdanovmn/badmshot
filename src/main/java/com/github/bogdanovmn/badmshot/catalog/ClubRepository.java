package com.github.bogdanovmn.badmshot.catalog;

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
public class ClubRepository {

    private static final RowMapper<Club> CLUB_ROW_MAPPER = (rs, rowNum) -> Club.builder()
        .id(UUID.fromString(rs.getString("id")))
        .name(rs.getString("name"))
        .build();

    private final NamedParameterJdbcTemplate jdbc;

    public UUID upsert(String name) {
        return jdbc.queryForObject("""
                INSERT INTO club (name)
                VALUES (:name)
                ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """,
            Map.of("name", name),
            UUID.class
        );
    }

    public Optional<Club> findById(UUID clubId) {
        return jdbc.query("""
                SELECT id, name
                FROM club
                WHERE id = :clubId
                """,
            Map.of("clubId", clubId),
            CLUB_ROW_MAPPER
        ).stream().findFirst();
    }

    public List<Club> search(String term, UUID playerId, int limit) {
        Optional<String> query = FullTextQuery.of(term);
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.name
                FROM club c
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        query.ifPresent(q -> {
            sql.append(" AND c.search_vector @@ to_tsquery('simple', :tsq)");
            params.addValue("tsq", q);
        });
        if (playerId != null) {
            sql.append("""
                 AND EXISTS (
                    SELECT 1
                    FROM match m
                    JOIN match_player mp ON mp.match_id = m.id
                    WHERE m.club_id = c.id AND mp.player_id = :playerId
                 )
                """);
            params.addValue("playerId", playerId);
        }
        sql.append(" ORDER BY c.name LIMIT :limit");
        return jdbc.query(sql.toString(), params, CLUB_ROW_MAPPER);
    }

    public List<Club> listAll(int limit) {
        return jdbc.query("""
                SELECT id, name
                FROM club
                ORDER BY name
                LIMIT :limit
                """,
            Map.of("limit", limit),
            CLUB_ROW_MAPPER
        );
    }

    public List<UUID> existingIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return jdbc.queryForList("""
                SELECT id FROM club WHERE id IN (:ids)
                """,
            Map.of("ids", ids),
            UUID.class
        );
    }
}
