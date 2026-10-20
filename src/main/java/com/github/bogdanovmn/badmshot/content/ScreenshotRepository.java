package com.github.bogdanovmn.badmshot.content;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ScreenshotRepository {

    private static final RowMapper<Screenshot> SCREENSHOT_ROW_MAPPER = (rs, rowNum) -> Screenshot.builder()
        .id(UUID.fromString(rs.getString("id")))
        .matchId(UUID.fromString(rs.getString("match_id")))
        .timecodeSeconds(rs.getInt("timecode_seconds"))
        .imageFormat(rs.getString("image_format"))
        .width(rs.getInt("width"))
        .height(rs.getInt("height"))
        .createdAt(rs.getTimestamp("created_at").toInstant())
        .build();

    private static final RowMapper<ScreenshotCard> CARD_ROW_MAPPER = (rs, rowNum) -> ScreenshotCard.builder()
        .id(UUID.fromString(rs.getString("id")))
        .matchId(UUID.fromString(rs.getString("match_id")))
        .timecodeSeconds(rs.getInt("timecode_seconds"))
        .imageFormat(rs.getString("image_format"))
        .width(rs.getInt("width"))
        .height(rs.getInt("height"))
        .createdAt(rs.getTimestamp("created_at").toInstant())
        .year((Integer) rs.getObject("year"))
        .month((Integer) rs.getObject("month"))
        .matchDate(rs.getObject("match_date", LocalDate.class))
        .clubId(rs.getString("club_id") == null ? null : UUID.fromString(rs.getString("club_id")))
        .clubName(rs.getString("club_name"))
        .tournament(rs.getBoolean("tournament"))
        .players(playerNames(rs))
        .likes(rs.getLong("likes"))
        .liked(rs.getBoolean("liked"))
        .build();

    private static final String CARD_SELECT = """
        SELECT s.id, s.match_id, s.timecode_seconds, s.image_format, s.width, s.height, s.created_at,
               m.year, m.month, m.match_date, m.tournament, m.club_id,
               c.name AS club_name,
               (SELECT count(*) FROM screenshot_like l WHERE l.screenshot_id = s.id) AS likes,
               EXISTS (
                   SELECT 1 FROM screenshot_like l2
                   WHERE l2.screenshot_id = s.id AND l2.user_id = :userId
               ) AS liked,
               array_agg(coalesce(p.display_name, p.name) ORDER BY mp.position)
                   FILTER (WHERE p.id IS NOT NULL) AS players
        FROM screenshot s
        JOIN match m ON m.id = s.match_id
        LEFT JOIN club c ON c.id = m.club_id
        LEFT JOIN match_player mp ON mp.match_id = m.id
        LEFT JOIN player p ON p.id = mp.player_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public UUID insert(UUID matchId, int timecodeSeconds, byte[] image, String format, int width, int height) {
        return jdbc.queryForObject("""
                INSERT INTO screenshot (match_id, timecode_seconds, image, image_format, width, height)
                VALUES (:matchId, :timecodeSeconds, :image, :format, :width, :height)
                RETURNING id
                """,
            new MapSqlParameterSource()
                .addValue("matchId", matchId)
                .addValue("timecodeSeconds", timecodeSeconds)
                .addValue("image", image, Types.BINARY)
                .addValue("format", format)
                .addValue("width", width)
                .addValue("height", height),
            UUID.class
        );
    }

    public int countByMatch(UUID matchId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM screenshot WHERE match_id = :matchId
                """,
            Map.of("matchId", matchId),
            Integer.class
        );
    }

    public void deleteByMatch(UUID matchId) {
        jdbc.update("""
                DELETE FROM screenshot WHERE match_id = :matchId
                """,
            Map.of("matchId", matchId)
        );
    }

    public List<Screenshot> byMatch(UUID matchId) {
        return jdbc.query("""
                SELECT id, match_id, timecode_seconds, image_format, width, height, created_at
                FROM screenshot
                WHERE match_id = :matchId
                ORDER BY timecode_seconds
                """,
            Map.of("matchId", matchId),
            SCREENSHOT_ROW_MAPPER
        );
    }

    public Optional<Screenshot> findById(UUID id) {        return jdbc.query("""
                SELECT id, match_id, timecode_seconds, image_format, width, height, created_at
                FROM screenshot
                WHERE id = :id
                """,
            Map.of("id", id),
            SCREENSHOT_ROW_MAPPER
        ).stream().findFirst();
    }

    public Optional<ScreenshotImage> image(UUID id) {
        return jdbc.query("""
                SELECT image, image_format, width, height
                FROM screenshot
                WHERE id = :id
                """,
            Map.of("id", id),
            (rs, rowNum) -> ScreenshotImage.builder()
                .data(rs.getBytes("image"))
                .format(rs.getString("image_format"))
                .width(rs.getInt("width"))
                .height(rs.getInt("height"))
                .build()
        ).stream().findFirst();
    }

    public Optional<ScreenshotCard> randomCard(
        ScreenshotFilter filter,
        UUID userId,
        UUID excludeMatchId,
        UUID excludeClubId
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limit", 1);
        StringBuilder sql = new StringBuilder(CARD_SELECT);
        applyFilter(sql, params, filter);
        sql.append("""

            GROUP BY s.id, m.id, c.id
            """);
        sql.append(randomOrder(params, excludeMatchId, excludeClubId));
        sql.append("""
             LIMIT :limit
            """);
        return jdbc.query(sql.toString(), params, CARD_ROW_MAPPER).stream().findFirst();
    }

    private String randomOrder(MapSqlParameterSource params, UUID excludeMatchId, UUID excludeClubId) {
        List<String> keys = new ArrayList<>();
        if (excludeClubId != null) {
            params.addValue("excludeClubId", excludeClubId);
            keys.add("(m.club_id IS NOT DISTINCT FROM :excludeClubId)");
        }
        if (excludeMatchId != null) {
            params.addValue("excludeMatchId", excludeMatchId);
            keys.add("(m.id = :excludeMatchId)");
        }
        keys.add("random()");
        return " ORDER BY " + String.join(", ", keys);
    }

    public List<ScreenshotCard> topCards(UUID userId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limit", limit);
        StringBuilder sql = new StringBuilder(CARD_SELECT);
        sql.append("""

            GROUP BY s.id, m.id, c.id
            ORDER BY likes DESC, s.created_at DESC
            LIMIT :limit
            """);
        return jdbc.query(sql.toString(), params, CARD_ROW_MAPPER);
    }

    public List<ScreenshotCard> likedCards(UUID userId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limit", limit);
        StringBuilder sql = new StringBuilder(CARD_SELECT);
        sql.append("""

            JOIN screenshot_like sl ON sl.screenshot_id = s.id AND sl.user_id = :userId
            GROUP BY s.id, m.id, c.id, sl.created_at
            ORDER BY sl.created_at DESC
            LIMIT :limit
            """);
        return jdbc.query(sql.toString(), params, CARD_ROW_MAPPER);
    }

    public Optional<ScreenshotCard> card(UUID id, UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("id", id);
        StringBuilder sql = new StringBuilder(CARD_SELECT);
        sql.append("""

            WHERE s.id = :id
            GROUP BY s.id, m.id, c.id
            """);
        return jdbc.query(sql.toString(), params, CARD_ROW_MAPPER).stream().findFirst();
    }

    public void like(UUID screenshotId, UUID userId) {
        jdbc.update("""
                INSERT INTO screenshot_like (screenshot_id, user_id)
                VALUES (:screenshotId, :userId)
                ON CONFLICT DO NOTHING
                """,
            Map.of("screenshotId", screenshotId, "userId", userId)
        );
    }

    public void unlike(UUID screenshotId, UUID userId) {
        jdbc.update("""
                DELETE FROM screenshot_like
                WHERE screenshot_id = :screenshotId AND user_id = :userId
                """,
            Map.of("screenshotId", screenshotId, "userId", userId)
        );
    }

    private void applyFilter(StringBuilder sql, MapSqlParameterSource params, ScreenshotFilter filter) {
        List<UUID> clubs = filter.clubsOrEmpty();
        List<UUID> players = filter.playersOrEmpty();
        if (!clubs.isEmpty()) {
            sql.append(" WHERE m.club_id IN (:clubs)");
            params.addValue("clubs", clubs);
        } else {
            sql.append(" WHERE 1 = 1");
        }
        if (!players.isEmpty()) {
            sql.append("""
                 AND m.id IN (
                    SELECT mp2.match_id
                    FROM match_player mp2
                    WHERE mp2.player_id IN (:players)
                    GROUP BY mp2.match_id
                    HAVING count(DISTINCT mp2.player_id) = :playersCount
                 )
                """);
            params.addValue("players", players);
            params.addValue("playersCount", players.size());
        }
    }

    private static List<String> playerNames(ResultSet rs) throws SQLException {
        Array array = rs.getArray("players");
        if (array == null) {
            return List.of();
        }
        String[] values = (String[]) array.getArray();
        return values == null ? List.of() : Arrays.asList(values);
    }
}
