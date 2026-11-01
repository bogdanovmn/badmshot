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
    long framesWithLikes;
    long framesWithShares;
    long views;
    long comments;
}
