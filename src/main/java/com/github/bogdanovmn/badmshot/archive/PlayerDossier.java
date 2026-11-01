package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.Player;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
class PlayerDossier {
    Player player;
    PlayerDossierStats stats;
    List<PlayerClubStat> clubs;
    List<PlayerRelationStat> partners;
    List<PlayerRelationStat> opponents;
}
