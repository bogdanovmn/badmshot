package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class ArchiveFile {
    String name;
    byte[] data;
}
