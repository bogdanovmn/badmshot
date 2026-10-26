package com.github.bogdanovmn.badmshot.archive;

import com.github.bogdanovmn.badmshot.catalog.Player;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PlayerRenameInfo {
    Player player;
    List<PlayerRenameMatch> matches;
}
