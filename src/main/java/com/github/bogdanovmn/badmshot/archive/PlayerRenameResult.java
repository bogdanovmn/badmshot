package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlayerRenameResult {
    String name;
    int renamedFiles;
}
