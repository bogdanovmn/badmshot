package com.github.bogdanovmn.badmshot.catalog;

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
        .displayName(rs.getString("display_name"))
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
                SELECT id, name, display_name
                FROM club
                WHERE id = :clubId
                """,
            Map.of("clubId", clubId),
            CLUB_ROW_MAPPER
        ).stream().findFirst();
    }

    public List<Club> list(UUID playerId) {
        if (playerId == null) {
            return listAll();
        }
        return jdbc.query("""
                SELECT c.id, c.name, c.display_name
                FROM club c
                WHERE EXISTS (
                    SELECT 1
                    FROM match m
                    JOIN match_player mp ON mp.match_id = m.id
                    WHERE m.club_id = c.id AND mp.player_id = :playerId
                )
                ORDER BY coalesce(c.display_name, c.name)
                """,
            Map.of("playerId", playerId),
            CLUB_ROW_MAPPER
        );
    }

    public List<Club> listAll() {
        return jdbc.query("""
                SELECT id, name, display_name
                FROM club
                ORDER BY coalesce(display_name, name)
                """,
            CLUB_ROW_MAPPER
        );
    }

    public List<Club> byIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT id, name, display_name
                FROM club
                WHERE id IN (:ids)
                ORDER BY coalesce(display_name, name)
                """,
            Map.of("ids", ids),
            CLUB_ROW_MAPPER
        );
    }

    public List<CatalogTag> tags() {
        return jdbc.query("""
                SELECT c.id, coalesce(nullif(c.display_name, ''), c.name) AS label, count(m.id) AS matches
                FROM club c
                JOIN match m ON m.club_id = c.id
                GROUP BY c.id, c.name, c.display_name
                ORDER BY label
                """,
            (rs, rowNum) -> CatalogTag.builder()
                .id(UUID.fromString(rs.getString("id")))
                .label(rs.getString("label"))
                .matches(rs.getLong("matches"))
                .build()
        );
    }

    public void update(UUID clubId, String displayName) {
        jdbc.update("""
                UPDATE club
                SET display_name = :displayName
                WHERE id = :clubId
                """,
            new MapSqlParameterSource()
                .addValue("clubId", clubId)
                .addValue("displayName", displayName)
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
