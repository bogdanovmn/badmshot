package com.github.bogdanovmn.badmshot.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/frame-effects")
@RequiredArgsConstructor
class FrameEffectsController {

    private final FrameEffectsRepository repository;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    String get() {
        return repository.find().orElse("{}");
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('admin')")
    void save(@RequestBody String settings) {
        repository.save(settings);
    }
}
