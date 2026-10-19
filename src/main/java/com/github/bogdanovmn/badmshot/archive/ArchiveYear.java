package com.github.bogdanovmn.badmshot.archive;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ArchiveYear {
    int year;
    int matches;
    int invalid;
    List<ArchiveMonth> months;
}
