package com.github.bogdanovmn.badmshot.catalog;

import com.github.bogdanovmn.badmshot.common.FrameFilterConstraints;
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
    private final FrameFilterConstraints filterConstraints;

    @Value("${badmshot.filters.limit:50}")
    private int limit;

    @GetMapping("/clubs")
    List<Club> clubs(@RequestParam(name = "playerId", required = false) UUID playerId) {
        if (playerId == null) {
            return clubRepository.list(null);
        }
        return clubRepository.list(playerRepository.canonicalId(playerId).orElse(playerId));
    }

    /**
     * Согласованный набор опций для формы фильтра: клубы и доступность
     * индивидуальных тренировок рассчитываются под текущий выбор игроков.
     */
    @GetMapping("/options")
    FilterOptions options(
        @RequestParam(name = "players", required = false) List<UUID> players,
        @RequestParam(name = "together", defaultValue = "false") boolean together,
        @RequestParam(name = "indi", defaultValue = "false") boolean includeIndi
    ) {
        List<UUID> playerIds = playerRepository.canonicalIds(players == null ? List.of() : players);
        int maxTogetherPlayers = filterConstraints.maxTogetherPlayers();
        boolean jointFrames = playerRepository.jointFramesAvailable(playerIds, includeIndi);
        boolean framesAvailable = together
            ? jointFrames
            : playerRepository.anyFramesAvailable(playerIds, includeIndi);
        return FilterOptions.builder()
            .clubs(clubRepository.eligibleForFrames(playerIds, together))
            .indiAvailable(playerRepository.indiAvailable(playerIds, together))
            .togetherAvailable(playerIds.size() <= maxTogetherPlayers && jointFrames)
            .framesAvailable(framesAvailable)
            .maxTogetherPlayers(maxTogetherPlayers)
            .build();
    }

    @GetMapping("/players")
    List<Player> players(
        @RequestParam(name = "clubId", required = false) UUID clubId,
        @RequestParam(name = "term", required = false) String term,
        @RequestParam(name = "letter", required = false) String letter,
        @RequestParam(name = "unprocessed", defaultValue = "false") boolean unprocessed,
        @RequestParam(name = "players", required = false) List<UUID> selectedPlayers,
        @RequestParam(name = "together", defaultValue = "false") boolean together,
        @RequestParam(name = "indi", defaultValue = "false") boolean includeIndi,
        @RequestParam(name = "availableOnly", defaultValue = "false") boolean availableOnly
    ) {
        if (letter != null && !letter.isBlank()) {
            return playerRepository.byLetter(letter, unprocessed);
        }
        return playerRepository.search(
            term,
            clubId,
            limit,
            playerRepository.canonicalIds(selectedPlayers == null ? List.of() : selectedPlayers),
            together,
            includeIndi,
            availableOnly
        );
    }

    @GetMapping("/players/letters")
    List<PlayerLetterCount> playerLetters(
        @RequestParam(name = "unprocessed", defaultValue = "false") boolean unprocessed
    ) {
        return playerRepository.letters(unprocessed);
    }

    @GetMapping("/players/no-games")
    List<Player> playersWithoutMatches(
        @RequestParam(name = "unprocessed", defaultValue = "false") boolean unprocessed
    ) {
        return playerRepository.withoutMatches(unprocessed);
    }

    @GetMapping("/clubs/by-ids")
    List<Club> clubsByIds(@RequestParam(name = "ids", required = false) List<UUID> ids) {
        return ids == null ? List.of() : clubRepository.byIds(ids);
    }

    @GetMapping("/players/by-ids")
    List<Player> playersByIds(@RequestParam(name = "ids", required = false) List<UUID> ids) {
        return ids == null ? List.of() : playerRepository.byIds(ids);
    }
}
