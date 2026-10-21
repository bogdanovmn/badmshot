package com.github.bogdanovmn.badmshot.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class FrameEffectsRepository {

    private static final int SETTINGS_ID = 1;

    private final NamedParameterJdbcTemplate jdbc;

    Optional<String> find() {
        return jdbc.query(
                """
                SELECT settings::text
                FROM frame_effects
                WHERE id = :id
                """,
                Map.of("id", SETTINGS_ID),
                (rs, rowNum) -> rs.getString(1)
            )
            .stream()
            .findFirst();
    }

    void save(String settings) {
        jdbc.update(
            """
            INSERT INTO frame_effects (id, settings, updated_at)
            VALUES (:id, :settings::jsonb, now())
            ON CONFLICT (id) DO UPDATE
            SET settings = :settings::jsonb,
                updated_at = now()
            """,
            Map.of(
                "id", SETTINGS_ID,
                "settings", settings
            )
        );
    }
}
