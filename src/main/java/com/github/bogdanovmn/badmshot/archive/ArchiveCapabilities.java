package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class ArchiveCapabilities {
    boolean exportEnabled;
    boolean importEnabled;
    boolean indexEnabled;
    boolean filesReadOnly;
}
