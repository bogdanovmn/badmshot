package com.github.bogdanovmn.badmshot.archive;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class StatisticsRepository {

    private static final RowMapper<CatalogStat> CATALOG_STAT_ROW_MAPPER = (rs, rowNum) -> CatalogStat.builder()
        .id(UUID.fromString(rs.getString("id")))
        .name(rs.getString("name"))
        .matches(rs.getInt("matches"))
        .sizeMbytes(rs.getLong("size_mbytes"))
        .durationSeconds(rs.getLong("duration_seconds"))
        .build();

    private final NamedParameterJdbcTemplate jdbc;

    public ArchiveStats stats() {
        List<ArchiveYearStat> years = jdbc.query("""
                SELECT s.year, s.month,
                       count(*) AS matches,
                       coalesce(sum(m.size_mbytes), 0) AS size_mbytes,
                       coalesce(sum(m.duration_seconds), 0) AS duration_seconds
                FROM match m
                JOIN archive_session s ON s.id = m.session_id
                WHERE m.validation_error IS NULL AND s.year IS NOT NULL
                GROUP BY s.year, s.month
                ORDER BY s.year DESC, s.month DESC
                """,
            Map.of(),
            (ResultSetExtractor<List<ArchiveYearStat>>) rs -> {
                LinkedHashMap<Integer, List<ArchiveMonthStat>> byYear = new LinkedHashMap<>();
                while (rs.next()) {
                    byYear.computeIfAbsent(rs.getInt("year"), y -> new ArrayList<>()).add(
                        ArchiveMonthStat.builder()
                            .month(rs.getInt("month"))
                            .matches(rs.getInt("matches"))
                            .sizeMbytes(rs.getLong("size_mbytes"))
                            .durationSeconds(rs.getLong("duration_seconds"))
                            .build()
                    );
                }
                return byYear.entrySet().stream()
                    .map(entry -> ArchiveYearStat.builder()
                        .year(entry.getKey())
                        .matches(entry.getValue().stream().mapToInt(ArchiveMonthStat::getMatches).sum())
                        .sizeMbytes(entry.getValue().stream().mapToLong(ArchiveMonthStat::getSizeMbytes).sum())
                        .durationSeconds(entry.getValue().stream().mapToLong(ArchiveMonthStat::getDurationSeconds).sum())
                        .months(entry.getValue())
                        .build()
                    )
                    .toList();
            }
        );

        List<CatalogStat> clubs = jdbc.query("""
                SELECT c.id, coalesce(c.display_name, c.name) AS name, count(*) AS matches,
                       coalesce(sum(m.size_mbytes), 0) AS size_mbytes,
                       coalesce(sum(m.duration_seconds), 0) AS duration_seconds
                FROM match m
                JOIN archive_session s ON s.id = m.session_id
                JOIN club c ON c.id = s.club_id
                WHERE m.validation_error IS NULL
                GROUP BY c.id, c.name, c.display_name
                ORDER BY matches DESC, name
                """,
            Map.of(),
            CATALOG_STAT_ROW_MAPPER
        );

        List<CatalogStat> players = jdbc.query("""
                SELECT p.id, coalesce(nullif(p.display_name, ''), p.name) AS name, count(*) AS matches,
                       coalesce(sum(m.size_mbytes), 0) AS size_mbytes,
                       coalesce(sum(m.duration_seconds), 0) AS duration_seconds
                FROM (
                    SELECT DISTINCT coalesce(pp.alias_of_player_id, pp.id) AS root_id, m.id AS match_id
                    FROM match_player mp
                    JOIN match m ON m.id = mp.match_id
                    JOIN player pp ON pp.id = mp.player_id
                    WHERE m.validation_error IS NULL
                ) pm
                JOIN player p ON p.id = pm.root_id
                JOIN match m ON m.id = pm.match_id
                GROUP BY p.id, p.name, p.display_name
                ORDER BY matches DESC, name
                """,
            Map.of(),
            CATALOG_STAT_ROW_MAPPER
        );

        Map<String, Object> screenshots = jdbc.queryForObject("""
                SELECT count(*) AS frames,
                       coalesce(sum(octet_length(image)::bigint), 0) AS size_bytes
                FROM screenshot
                """,
            Map.of(),
            (rs, rowNum) -> Map.<String, Object>of(
                "frames", rs.getLong("frames"),
                "sizeBytes", rs.getLong("size_bytes")
            )
        );

        return ArchiveStats.builder()
            .matches(years.stream().mapToInt(ArchiveYearStat::getMatches).sum())
            .sizeMbytes(years.stream().mapToLong(ArchiveYearStat::getSizeMbytes).sum())
            .durationSeconds(years.stream().mapToLong(ArchiveYearStat::getDurationSeconds).sum())
            .screenshotCount((Long) screenshots.get("frames"))
            .screenshotSizeMbytes((Long) screenshots.get("sizeBytes") / (1024 * 1024))
            .years(years)
            .clubs(clubs)
            .players(players)
            .build();
    }
}
