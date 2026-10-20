package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
class MatchPage {
    int total;
    List<MatchBrief> items;
}
