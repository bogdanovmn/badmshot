package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.UUID;

@Value
@Builder
class LoadedArchive {
    SessionManifest manifest;
    Map<UUID, byte[]> images;
}
