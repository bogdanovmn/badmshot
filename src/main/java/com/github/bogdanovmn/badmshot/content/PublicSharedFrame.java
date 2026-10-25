package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
class PublicSharedFrame {
    UUID token;
    int timecodeSeconds;
    String imageFormat;
    int width;
    int height;
    Instant createdAt;
    Integer year;
    Integer month;
    LocalDate matchDate;
    String clubName;
    boolean tournament;
    List<String> players;
    long likes;
    long comments;
    long views;
    long shareViews;

    static PublicSharedFrame from(SharedFrame shared) {
        ScreenshotCard card = shared.getCard();
        return PublicSharedFrame.builder()
            .token(shared.getToken())
            .timecodeSeconds(card.getTimecodeSeconds())
            .imageFormat(card.getImageFormat())
            .width(card.getWidth())
            .height(card.getHeight())
            .createdAt(card.getCreatedAt())
            .year(card.getYear())
            .month(card.getMonth())
            .matchDate(card.getMatchDate())
            .clubName(card.getClubName())
            .tournament(card.isTournament())
            .players(card.getPlayers())
            .likes(card.getLikes())
            .comments(card.getComments())
            .views(card.getViews())
            .shareViews(shared.getViews())
            .build();
    }
}
