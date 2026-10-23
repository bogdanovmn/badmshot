package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.job.JobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/archive/import")
@RequiredArgsConstructor
class ArchiveImportController {

    private final ArchiveImportService importService;

    @PostMapping
    JobResponse start(@RequestParam("file") MultipartFile file) {
        return JobResponse.of(importService.start(file));
    }

    @GetMapping
    ResponseEntity<JobResponse> current() {
        return importService.current()
            .map(job -> ResponseEntity.ok(JobResponse.of(job)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    List<JobResponse> history(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        return importService.history(limit).stream()
            .map(JobResponse::of)
            .toList();
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel() {
        importService.cancel();
    }
}
