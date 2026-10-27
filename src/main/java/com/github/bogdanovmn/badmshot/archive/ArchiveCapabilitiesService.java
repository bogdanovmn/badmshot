package com.github.bogdanovmn.badmshot.archive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class ArchiveCapabilitiesService {

    @Value("${badmshot.export.enabled:true}")
    private boolean exportEnabled;

    @Value("${badmshot.import.enabled:true}")
    private boolean importEnabled;

    @Value("${badmshot.index.enabled:true}")
    private boolean indexEnabled;

    @Value("${badmshot.archive.files-read-only:false}")
    private boolean filesReadOnly;

    ArchiveCapabilities capabilities() {
        return ArchiveCapabilities.builder()
            .exportEnabled(exportEnabled)
            .importEnabled(importEnabled)
            .indexEnabled(indexEnabled)
            .filesReadOnly(filesReadOnly)
            .build();
    }
}
