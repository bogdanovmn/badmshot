package com.github.bogdanovmn.badmshot.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
class PlayerController {

    private final PlayerRepository playerRepository;

    @PutMapping("/{id}")
    void update(@PathVariable("id") UUID id, @RequestBody PlayerUpdate request) {
        playerRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Игрок не найден: " + id));
        playerRepository.update(id, request.getDisplayName(), request.getTournamentAlias());
    }
}
