package com.github.bogdanovmn.badmshot.content;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class ScreenshotCommentRepository {

    private static final RowMapper<ScreenshotComment> COMMENT_ROW_MAPPER = (rs, rowNum) -> ScreenshotComment.builder()
        .id(UUID.fromString(rs.getString("id")))
        .screenshotId(UUID.fromString(rs.getString("screenshot_id")))
        .author(rs.getString("author"))
        .text(rs.getString("text"))
        .createdAt(rs.getTimestamp("created_at").toInstant())
        .build();

    private final NamedParameterJdbcTemplate jdbc;

    List<ScreenshotComment> byScreenshot(UUID screenshotId) {
        return jdbc.query("""
                SELECT sc.id, sc.screenshot_id, sc.text, sc.created_at,
                       coalesce(u.display_name, 'Игрок') AS author
                FROM screenshot_comment sc
                LEFT JOIN app_user u ON u.id = sc.user_id
                WHERE sc.screenshot_id = :screenshotId
                ORDER BY sc.created_at, sc.id
                """,
            Map.of("screenshotId", screenshotId),
            COMMENT_ROW_MAPPER
        );
    }

    ScreenshotComment create(UUID screenshotId, UUID userId, String text) {
        return jdbc.queryForObject("""
                WITH inserted AS (
                    INSERT INTO screenshot_comment (screenshot_id, user_id, text)
                    VALUES (:screenshotId, :userId, :text)
                    RETURNING id, screenshot_id, text, created_at, user_id
                )
                SELECT i.id, i.screenshot_id, i.text, i.created_at,
                       coalesce(u.display_name, 'Игрок') AS author
                FROM inserted i
                LEFT JOIN app_user u ON u.id = i.user_id
                """,
            new MapSqlParameterSource()
                .addValue("screenshotId", screenshotId)
                .addValue("userId", userId)
                .addValue("text", text),
            COMMENT_ROW_MAPPER
        );
    }
}
