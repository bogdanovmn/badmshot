package com.github.bogdanovmn.badmshot.content;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Карточка кадра. Spring по умолчанию исключает из вида поля без {@code @JsonView},
 * поэтому публичные поля размечены явно: {@code PublicView} — общий ответ (ТОП),
 * {@link PersonalView} — дополнительно отдаёт персональный флаг {@code liked}.
 */
@Value
@Builder
class ScreenshotCard {

    /** Публичный ответ: без персонального флага {@code liked} (например, ТОП, кэшируемый nginx). */
    interface PublicView {
    }

    /** Персональный ответ: полный набор полей (страница кадра, ленты). */
    interface PersonalView extends PublicView {
    }

    @JsonView(PublicView.class)
    UUID id;
    @JsonView(PublicView.class)
    UUID matchId;
    @JsonView(PublicView.class)
    int timecodeSeconds;
    @JsonView(PublicView.class)
    String imageFormat;
    @JsonView(PublicView.class)
    int width;
    @JsonView(PublicView.class)
    int height;
    @JsonView(PublicView.class)
    Instant createdAt;
    @JsonView(PublicView.class)
    Integer year;
    @JsonView(PublicView.class)
    Integer month;
    @JsonView(PublicView.class)
    LocalDate matchDate;
    @JsonView(PublicView.class)
    UUID clubId;
    @JsonView(PublicView.class)
    String clubName;
    @JsonView(PublicView.class)
    boolean tournament;
    @JsonView(PublicView.class)
    List<String> players;
    @JsonView(PublicView.class)
    long likes;
    @JsonView(PublicView.class)
    long shares;
    @JsonView(PublicView.class)
    long comments;
    @JsonView(PublicView.class)
    Instant lastCommentAt;
    @JsonView(PersonalView.class)
    boolean liked;
    @JsonView(PublicView.class)
    long views;
}
