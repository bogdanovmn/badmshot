package com.github.bogdanovmn.badmshot.stats;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class SiteActivityStats {
    long users;
    long likes;
    long shares;
    long views;
    long comments;
}
