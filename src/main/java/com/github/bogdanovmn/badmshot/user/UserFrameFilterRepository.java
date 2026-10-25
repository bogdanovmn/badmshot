package com.github.bogdanovmn.badmshot.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class UserFrameFilterRepository {

    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    Optional<UserFrameFilter> find(UUID userId) {
        return jdbc.query("""
                SELECT player_ids::text, club_ids::text, include_indi, updated_at
                FROM user_frame_filter
                WHERE user_id = :userId
                """,
            Map.of("userId", userId),
            (rs, rowNum) -> UserFrameFilter.builder()
                .playerIds(readIds(rs.getString("player_ids")))
                .clubIds(readIds(rs.getString("club_ids")))
                .includeIndi(rs.getBoolean("include_indi"))
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build()
        ).stream().findFirst();
    }

    void save(UUID userId, List<UUID> playerIds, List<UUID> clubIds, boolean includeIndi) {
        jdbc.update("""
                INSERT INTO user_frame_filter (user_id, player_ids, club_ids, include_indi, updated_at)
                VALUES (:userId, :playerIds::jsonb, :clubIds::jsonb, :includeIndi, now())
                ON CONFLICT (user_id) DO UPDATE
                SET player_ids = EXCLUDED.player_ids,
                    club_ids = EXCLUDED.club_ids,
                    include_indi = EXCLUDED.include_indi,
                    updated_at = now()
                """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("playerIds", writeIds(playerIds))
                .addValue("clubIds", writeIds(clubIds))
                .addValue("includeIndi", includeIndi)
        );
    }

    private List<UUID> readIds(String value) {
        try {
            return objectMapper.readValue(value, UUID_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Can't read frame filter", e);
        }
    }

    private String writeIds(List<UUID> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Can't write frame filter", e);
        }
    }
}
