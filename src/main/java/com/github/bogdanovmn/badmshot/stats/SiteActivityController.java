package com.github.bogdanovmn.badmshot.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
class SiteActivityController {

    private final SiteActivityRepository activityRepository;

    @GetMapping("/activity")
    SiteActivityStats activity() {
        return activityRepository.stats();
    }
}
