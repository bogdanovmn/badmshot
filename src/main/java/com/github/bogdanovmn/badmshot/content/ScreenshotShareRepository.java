package com.github.bogdanovmn.badmshot.content;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class ScreenshotShareRepository {

    private final NamedParameterJdbcTemplate jdbc;

    UUID createOrGet(UUID screenshotId, UUID userId) {
        return jdbc.queryForObject("""
                INSERT INTO screenshot_share (screenshot_id, user_id)
                VALUES (:screenshotId, :userId)
                ON CONFLICT (screenshot_id, user_id) DO UPDATE
                    SET screenshot_id = EXCLUDED.screenshot_id
                RETURNING id
                """,
            Map.of("screenshotId", screenshotId, "userId", userId),
            UUID.class
        );
    }

    Optional<UUID> screenshotId(UUID token) {
        return jdbc.queryForList("""
                SELECT screenshot_id
                FROM screenshot_share
                WHERE id = :token
                """,
            Map.of("token", token),
            UUID.class
        ).stream().findFirst();
    }

    void registerView(UUID token, String visitorKey, int cooldownSeconds) {
        jdbc.update("""
                WITH accepted AS (
                    INSERT INTO screenshot_share_view (share_id, visitor_key, viewed_at)
                    VALUES (:token, :visitorKey, now())
                    ON CONFLICT (share_id, visitor_key) DO UPDATE
                        SET viewed_at = EXCLUDED.viewed_at
                        WHERE screenshot_share_view.viewed_at
                            <= EXCLUDED.viewed_at - make_interval(secs => :cooldownSeconds)
                    RETURNING share_id
                ),
                shared AS (
                    UPDATE screenshot_share
                    SET views = views + 1
                    WHERE id IN (SELECT share_id FROM accepted)
                    RETURNING screenshot_id
                )
                UPDATE screenshot
                SET views = views + 1
                WHERE id IN (SELECT screenshot_id FROM shared)
                """,
            new MapSqlParameterSource()
                .addValue("token", token)
                .addValue("visitorKey", visitorKey)
                .addValue("cooldownSeconds", cooldownSeconds)
        );
    }
}
