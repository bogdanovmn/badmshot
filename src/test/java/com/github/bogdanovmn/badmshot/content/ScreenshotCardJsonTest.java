package com.github.bogdanovmn.badmshot.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotCardJsonTest {

    @Test
    void publicViewOmitsLikedFlag() throws Exception {
        ScreenshotCard card = ScreenshotCard.builder()
            .id(UUID.randomUUID())
            .matchId(UUID.randomUUID())
            .imageFormat("jpeg")
            .players(List.of("Игрок"))
            .liked(true)
            .build();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        String personal = mapper.writeValueAsString(card);
        String publicView = mapper.writerWithView(ScreenshotCard.PublicView.class)
            .writeValueAsString(card);

        assertTrue(personal.contains("\"liked\":true"));
        assertFalse(publicView.contains("liked"));
    }
}
