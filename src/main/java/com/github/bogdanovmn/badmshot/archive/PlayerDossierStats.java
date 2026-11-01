package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class PlayerDossierStats {
    int games;
    int trainings;
    long sizeMbytes;
    long durationSeconds;
    long screenshotCount;
}
