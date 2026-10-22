package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

@Value
@Builder
class ImportOptions {
    Path archiveFile;
    String originalName;
}
