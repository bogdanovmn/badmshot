package com.github.bogdanovmn.badmshot.catalog;

import com.github.bogdanovmn.badmshot.archive.ArchiveType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
class TagController {

    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;

    @GetMapping("/players")
    List<CatalogTag> players(@RequestParam(name = "type", defaultValue = "GAMES") ArchiveType type) {
        return playerRepository.tags(type);
    }

    @GetMapping("/clubs")
    List<CatalogTag> clubs(@RequestParam(name = "type", defaultValue = "GAMES") ArchiveType type) {
        return clubRepository.tags(type);
    }
}
