package com.github.bogdanovmn.badmshot.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/filters")
@RequiredArgsConstructor
class FilterController {

    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;

    @Value("${badmshot.filters.limit:50}")
    private int limit;

    @GetMapping("/clubs")
    List<Club> clubs(
        @RequestParam(name = "playerId", required = false) UUID playerId,
        @RequestParam(name = "term", required = false) String term
    ) {
        return clubRepository.search(term, playerId, limit);
    }

    @GetMapping("/players")
    List<Player> players(
        @RequestParam(name = "clubId", required = false) UUID clubId,
        @RequestParam(name = "term", required = false) String term
    ) {
        return playerRepository.search(term, clubId, limit);
    }
}
