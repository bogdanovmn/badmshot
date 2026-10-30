package com.github.bogdanovmn.badmshot.archive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class ArchiveCapabilitiesService {

    private final ArchiveCapabilities capabilities;

    ArchiveCapabilitiesService(
        @Value("${badmshot.export.enabled:true}") boolean exportEnabled,
        @Value("${badmshot.import.enabled:true}") boolean importEnabled,
        @Value("${badmshot.index.enabled:true}") boolean indexEnabled,
        @Value("${badmshot.archive.files-read-only:false}") boolean filesReadOnly
    ) {
        this.capabilities = ArchiveCapabilities.builder()
            .exportEnabled(exportEnabled)
            .importEnabled(importEnabled)
            .indexEnabled(indexEnabled)
            .filesReadOnly(filesReadOnly)
            .build();
    }

    ArchiveCapabilities capabilities() {
        return capabilities;
    }
}
