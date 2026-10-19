package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ScreenshotCard {
    UUID id;
    UUID matchId;
    int timecodeSeconds;
    String imageFormat;
    int width;
    int height;
    Instant createdAt;
    Integer year;
    Integer month;
    LocalDate matchDate;
    UUID clubId;
    String clubName;
    boolean tournament;
    List<String> players;
    long likes;
    boolean liked;
}
