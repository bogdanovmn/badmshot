package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
class ArchiveYear {
    int year;
    int matches;
    int invalid;
    int matched;
    List<ArchiveMonth> months;
}
