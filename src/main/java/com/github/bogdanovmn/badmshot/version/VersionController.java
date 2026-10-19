package com.github.bogdanovmn.badmshot.version;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/version")
class VersionController {

    @Value("${BACKEND_VERSION:dev}")
    private String version;

    @GetMapping
    Version version() {
        return Version.builder()
            .version(version)
            .build();
    }
}
