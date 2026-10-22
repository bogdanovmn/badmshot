package com.github.bogdanovmn.badmshot.archive;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/archive")
@RequiredArgsConstructor
class ArchiveController {

    private final ArchiveService archiveService;
    private final ArchiveSessionService sessionService;
    private final ArchiveExportService exportService;
    private final ArchiveCapabilitiesService capabilitiesService;

    @GetMapping("/capabilities")
    ArchiveCapabilities capabilities() {
        return capabilitiesService.capabilities();
    }

    @GetMapping("/sessions")
    ArchiveSessions sessions(
        @RequestParam(name = "year", required = false) Integer year,
        @RequestParam(name = "month", required = false) Integer month,
        @RequestParam(name = "clubId", required = false) UUID clubId,
        @RequestParam(name = "playerId", required = false) UUID playerId
    ) {
        return sessionService.sessions(year, month, clubId, playerId);
    }

    @GetMapping("/sessions/{id}/export")
    ResponseEntity<byte[]> export(@PathVariable("id") UUID id) {
        ArchiveFile file = exportService.export(id);
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(file.getName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(file.getData());
    }

    @GetMapping("/tree")
    List<ArchiveYear> tree() {
        return archiveService.tree();
    }

    @GetMapping("/matches")
    MatchPage matches(
        @RequestParam(name = "year", required = false) Integer year,
        @RequestParam(name = "month", required = false) Integer month,
        @RequestParam(name = "clubId", required = false) UUID clubId,
        @RequestParam(name = "playerId", required = false) UUID playerId,
        @RequestParam(name = "invalid", required = false) Boolean invalid,
        @RequestParam(name = "limit", defaultValue = "50") int limit,
        @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        return archiveService.briefs(year, month, clubId, playerId, invalid, limit, offset);
    }

    @GetMapping("/invalid")
    MatchPage invalid(
        @RequestParam(name = "limit", defaultValue = "50") int limit,
        @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        return archiveService.briefs(null, null, null, null, true, limit, offset);
    }

    @GetMapping("/matches/{id}")
    MatchDetail match(@PathVariable("id") UUID id) {
        return archiveService.detail(id);
    }

    @PostMapping("/matches/{id}/rename")
    MatchBrief rename(@PathVariable("id") UUID id, @RequestBody MatchRename request) {
        return archiveService.rename(id, request.getPlayers());
    }

    @GetMapping("/stats")
    ArchiveStats stats() {
        return archiveService.stats();
    }
}
