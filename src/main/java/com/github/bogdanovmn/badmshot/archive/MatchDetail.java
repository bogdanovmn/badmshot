package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.Player;
import com.github.bogdanovmn.badmshot.content.Screenshot;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
class MatchDetail {
    Match match;
    List<Player> players;
    List<Screenshot> screenshots;
}
