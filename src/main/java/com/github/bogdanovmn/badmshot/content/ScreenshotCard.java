package com.github.bogdanovmn.badmshot.content;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
class ScreenshotCard {

    /** Публичный ответ: без персонального флага {@code liked} (например, ТОП, кэшируемый nginx). */
    interface PublicView {
    }

    /** Персональный ответ: полный набор полей (страница кадра, ленты). */
    interface PersonalView extends PublicView {
    }

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
    long shares;
    long comments;
    Instant lastCommentAt;
    @JsonView(PersonalView.class)
    boolean liked;
    long views;
}
