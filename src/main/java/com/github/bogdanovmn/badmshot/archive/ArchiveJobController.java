package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.job.JobResponse;
import com.github.bogdanovmn.badmshot.job.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/archive/index-job")
@RequiredArgsConstructor
class ArchiveJobController {

    private final ArchiveIndexService indexService;
    private final JobService jobService;

    @PostMapping
    JobResponse start(@RequestBody(required = false) IndexRequest request) {
        return JobResponse.of(
            indexService.start(request == null ? IndexRequest.builder().build() : request)
        );
    }

    @GetMapping
    ResponseEntity<JobResponse> current() {
        return jobService.current(JobService.TYPE_INDEX)
            .map(job -> ResponseEntity.ok(JobResponse.of(job)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/defaults")
    IndexDefaults defaults() {
        return indexService.defaults();
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel() {
        jobService.cancel(JobService.TYPE_INDEX);
    }
}
