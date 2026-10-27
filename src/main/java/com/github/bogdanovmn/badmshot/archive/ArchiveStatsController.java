package com.github.bogdanovmn.badmshot.archive;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Статистика архива для раздела «Статистика» — доступна всем авторизованным пользователям,
 * в отличие от административного {@code /archive/stats}.
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
class ArchiveStatsController {

    private final ArchiveService archiveService;

    @GetMapping("/archive")
    ArchiveStats archive(@RequestParam(name = "type", defaultValue = "GAMES") ArchiveType type) {
        return archiveService.stats(type);
    }
}
