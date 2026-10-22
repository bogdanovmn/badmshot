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
        .views(rs.getLong("views"))
        .build();

    private static final String CARD_SELECT = """
        SELECT s.id, s.match_id, s.timecode_seconds, s.image_format, s.width, s.height, s.created_at, s.views,
               sess.year, sess.month, sess.match_date, sess.tournament, sess.club_id,
               coalesce(c.display_name, c.name) AS club_name,
               (SELECT count(*) FROM screenshot_like l WHERE l.screenshot_id = s.id) AS likes,
               EXISTS (
                   SELECT 1 FROM screenshot_like l2
                   WHERE l2.screenshot_id = s.id AND l2.user_id = :userId
               ) AS liked,
               array_agg(coalesce(root_player.display_name, root_player.name) ORDER BY mp.position)
                   FILTER (WHERE root_player.id IS NOT NULL) AS players
        FROM screenshot s
        JOIN match m ON m.id = s.match_id
        JOIN archive_session sess ON sess.id = m.session_id
        LEFT JOIN club c ON c.id = sess.club_id
        LEFT JOIN match_player mp ON mp.match_id = m.id
        LEFT JOIN player p ON p.id = mp.player_id
        LEFT JOIN player root_player ON root_player.id = coalesce(p.alias_of_player_id, p.id)
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

    public UUID insertImported(
        UUID id,
        UUID matchId,
        int timecodeSeconds,
        byte[] image,
        String format,
        int width,
        int height
    ) {
        jdbc.update("""
                INSERT INTO screenshot (id, match_id, timecode_seconds, image, image_format, width, height)
                VALUES (:id, :matchId, :timecodeSeconds, :image, :format, :width, :height)
                """,
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("matchId", matchId)
                .addValue("timecodeSeconds", timecodeSeconds)
                .addValue("image", image, Types.BINARY)
                .addValue("format", format)
                .addValue("width", width)
                .addValue("height", height)
        );
        return id;
    }

    public List<ScreenshotExport> exportBySession(UUID sessionId) {
        return jdbc.query("""
                SELECT s.id, s.match_id, s.timecode_seconds, s.image_format, s.width, s.height, s.image
                FROM screenshot s
                JOIN match m ON m.id = s.match_id
                WHERE m.session_id = :sessionId
                ORDER BY m.rel_path, s.timecode_seconds
                """,
            Map.of("sessionId", sessionId),
            (rs, rowNum) -> ScreenshotExport.builder()
                .id(UUID.fromString(rs.getString("id")))
                .matchId(UUID.fromString(rs.getString("match_id")))
                .timecodeSeconds(rs.getInt("timecode_seconds"))
                .imageFormat(rs.getString("image_format"))
                .width(rs.getInt("width"))
                .height(rs.getInt("height"))
                .data(rs.getBytes("image"))
                .build()
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

    public void deleteAll() {
        jdbc.update("""
                DELETE FROM screenshot
                """,
            Map.of()
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
        ServedFrame exclude
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limit", 1);
        StringBuilder sql = new StringBuilder(CARD_SELECT);
        applyFilter(sql, params, filter);
        sql.append("""

            GROUP BY s.id, m.id, sess.id, c.id
            """);
        sql.append(randomOrder(params, exclude));
        sql.append("""
             LIMIT :limit
            """);
        return jdbc.query(sql.toString(), params, CARD_ROW_MAPPER).stream().findFirst();
    }

    private String randomOrder(MapSqlParameterSource params, ServedFrame exclude) {
        List<String> keys = new ArrayList<>();
        if (exclude != null) {
            if (exclude.getScreenshotId() != null) {
                params.addValue("excludeScreenshotId", exclude.getScreenshotId());
                keys.add("(s.id = :excludeScreenshotId)");
            }
            if (exclude.getClubId() != null) {
                params.addValue("excludeClubId", exclude.getClubId());
                keys.add("(sess.club_id IS NOT DISTINCT FROM :excludeClubId)");
            }
            if (exclude.getMatchId() != null) {
                params.addValue("excludeMatchId", exclude.getMatchId());
                keys.add("(m.id = :excludeMatchId)");
            }
        }
        keys.add("random()");
        return " ORDER BY " + String.join(", ", keys);
    }

    public List<ScreenshotCard> teaserCards(UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId);
        StringBuilder sql = new StringBuilder(CARD_SELECT);
        sql.append("""
             WHERE s.id IN (
                 SELECT picked.id
                 FROM (
                     SELECT s2.id, row_number() OVER (
                         PARTITION BY sess2.club_id
                         ORDER BY random()
                     ) AS position
                     FROM screenshot s2
                     JOIN match m2 ON m2.id = s2.match_id
                     JOIN archive_session sess2 ON sess2.id = m2.session_id
                     WHERE sess2.club_id IS NOT NULL
                 ) picked
                 WHERE picked.position = 1
             )
             GROUP BY s.id, m.id, sess.id, c.id
             ORDER BY random()
            """);
        return jdbc.query(sql.toString(), params, CARD_ROW_MAPPER);
    }

    public int registerView(UUID screenshotId, UUID userId, int cooldownSeconds) {
        return jdbc.update("""
                WITH accepted AS (
                    INSERT INTO screenshot_view (screenshot_id, user_id, viewed_at)
                    VALUES (:screenshotId, :userId, now())
                    ON CONFLICT (screenshot_id, user_id) DO UPDATE
                        SET viewed_at = EXCLUDED.viewed_at
                        WHERE screenshot_view.viewed_at <= EXCLUDED.viewed_at - make_interval(secs => :cooldownSeconds)
                    RETURNING screenshot_id
                )
                UPDATE screenshot
                SET views = views + 1
                WHERE id IN (SELECT screenshot_id FROM accepted)
                """,
            new MapSqlParameterSource()
                .addValue("screenshotId", screenshotId)
                .addValue("userId", userId)
                .addValue("cooldownSeconds", cooldownSeconds)
        );
    }

    public List<ScreenshotCard> topCards(UUID userId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limit", limit);
        StringBuilder sql = new StringBuilder(CARD_SELECT);
        sql.append("""

            GROUP BY s.id, m.id, sess.id, c.id
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
            GROUP BY s.id, m.id, sess.id, c.id, sl.created_at
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
            GROUP BY s.id, m.id, sess.id, c.id
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
            sql.append(" WHERE sess.club_id IN (:clubs)");
            params.addValue("clubs", clubs);
        } else {
            sql.append(" WHERE 1 = 1");
        }
        if (!players.isEmpty()) {
            sql.append("""
                 AND m.id IN (
                    SELECT mp2.match_id
                    FROM match_player mp2
                    JOIN player p2 ON p2.id = mp2.player_id
                    WHERE coalesce(p2.alias_of_player_id, p2.id) IN (:players)
                    GROUP BY mp2.match_id
                    HAVING count(DISTINCT coalesce(p2.alias_of_player_id, p2.id)) = :playersCount
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
