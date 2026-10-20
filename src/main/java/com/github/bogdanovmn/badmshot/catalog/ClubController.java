package com.github.bogdanovmn.badmshot.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/clubs")
@RequiredArgsConstructor
class ClubController {

    private final ClubRepository clubRepository;

    @GetMapping
    List<Club> list() {
        return clubRepository.listAll();
    }

    @PutMapping("/{id}")
    void update(@PathVariable("id") UUID id, @RequestBody ClubUpdate request) {
        clubRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Клуб не найден: " + id));
        String displayName = request.getDisplayName();
        clubRepository.update(id, displayName == null || displayName.isBlank() ? null : displayName.trim());
    }
}
