package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ArchiveMonth {
    int month;
    int matches;
    int invalid;
}
