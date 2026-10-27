package com.github.bogdanovmn.badmshot.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotCardJsonTest {

    /**
     * Маппер создаётся так же, как в приложении: Spring отключает
     * {@code MapperFeature.DEFAULT_VIEW_INCLUSION}, поэтому все публичные поля
     * обязаны быть размечены {@code @JsonView(PublicView.class)}.
     */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private final ScreenshotCard card = ScreenshotCard.builder()
        .id(UUID.randomUUID())
        .matchId(UUID.randomUUID())
        .imageFormat("jpeg")
        .players(List.of("Игрок"))
        .likes(3)
        .liked(true)
        .views(7)
        .build();

    @Test
    void publicViewKeepsSharedFieldsButOmitsLikedFlag() throws Exception {
        String json = mapper.writerWithView(ScreenshotCard.PublicView.class)
            .writeValueAsString(card);

        assertTrue(json.contains("\"id\":"));
        assertTrue(json.contains("\"likes\":3"));
        assertTrue(json.contains("\"views\":7"));
        assertTrue(json.contains("\"players\":[\"Игрок\"]"));
        assertFalse(json.contains("liked"));
    }

    @Test
    void personalViewIncludesLikedFlag() throws Exception {
        String json = mapper.writerWithView(ScreenshotCard.PersonalView.class)
            .writeValueAsString(card);

        assertTrue(json.contains("\"liked\":true"));
        assertTrue(json.contains("\"likes\":3"));
    }

    @Test
    void defaultSerializationIncludesLikedFlag() throws Exception {
        assertTrue(mapper.writeValueAsString(card).contains("\"liked\":true"));
    }
}
