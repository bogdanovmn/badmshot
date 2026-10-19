package com.github.bogdanovmn.badmshot.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class UserRepository {

    private static final int DISPLAY_NAME_MAX_LENGTH = 100;
    private static final String DEFAULT_DISPLAY_NAME = "Player";

    private static final RowMapper<AppUser> USER_ROW_MAPPER = (rs, rowNum) -> AppUser.builder()
        .id(UUID.fromString(rs.getString("id")))
        .displayName(rs.getString("display_name"))
        .build();

    private final NamedParameterJdbcTemplate jdbc;

    Optional<AppUser> findById(UUID userId) {
        return jdbc.query("""
                SELECT id, display_name FROM app_user WHERE id = :userId
                """,
            Map.of("userId", userId),
            USER_ROW_MAPPER
        ).stream().findFirst();
    }

    AppUser getOrCreate(UUID userId, String displayName) {
        jdbc.update("""
                INSERT INTO app_user (id, display_name)
                VALUES (:userId, :displayName)
                ON CONFLICT (id) DO NOTHING
                """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("displayName", normalize(displayName))
        );
        return findById(userId).orElseThrow();
    }

    private String normalize(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        String trimmed = displayName.trim();
        return trimmed.length() > DISPLAY_NAME_MAX_LENGTH
            ? trimmed.substring(0, DISPLAY_NAME_MAX_LENGTH)
            : trimmed;
    }
}
