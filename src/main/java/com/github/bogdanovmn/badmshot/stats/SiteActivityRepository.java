package com.github.bogdanovmn.badmshot.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
class SiteActivityRepository {

    private final NamedParameterJdbcTemplate jdbc;

    SiteActivityStats stats() {
        return jdbc.queryForObject("""
                SELECT
                    (SELECT count(*) FROM app_user) AS users,
                    (SELECT count(*) FROM screenshot_like) AS likes,
                    (SELECT count(*) FROM screenshot_share) AS shares,
                    (SELECT coalesce(sum(views), 0) FROM screenshot) AS views,
                    (SELECT count(*) FROM screenshot_comment) AS comments
                """,
            Map.of(),
            (rs, rowNum) -> SiteActivityStats.builder()
                .users(rs.getLong("users"))
                .likes(rs.getLong("likes"))
                .shares(rs.getLong("shares"))
                .views(rs.getLong("views"))
                .comments(rs.getLong("comments"))
                .build()
        );
    }
}
