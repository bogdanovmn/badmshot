package com.github.bogdanovmn.badmshot.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
class TagController {

    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;

    @GetMapping("/players")
    List<CatalogTag> players() {
        return playerRepository.tags();
    }

    @GetMapping("/clubs")
    List<CatalogTag> clubs() {
        return clubRepository.tags();
    }
}
