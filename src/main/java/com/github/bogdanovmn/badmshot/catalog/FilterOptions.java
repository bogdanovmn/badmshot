package com.github.bogdanovmn.badmshot.catalog;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Доступные значения фильтра кадров для текущего выбора игроков.
 * Позволяет интерфейсу заранее показать только заведомо непустые комбинации.
 */
@Value
@Builder
class FilterOptions {
    List<Club> clubs;
    /**
     * Есть ли кадры индивидуальных тренировок для выбранных игроков
     * (всех вместе либо хотя бы одного — в зависимости от режима).
     */
    boolean indiAvailable;
    /**
     * Можно ли включить режим «вместе» для текущего набора игроков.
     */
    boolean togetherAvailable;
    /**
     * Есть ли хотя бы один кадр для текущего набора игроков и типа кадров.
     */
    boolean framesAvailable;
    int maxTogetherPlayers;
}
