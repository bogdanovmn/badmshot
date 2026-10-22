package com.github.bogdanovmn.badmshot.archive;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchivePathParserTest {

    private final ArchivePathParser parser = new ArchivePathParser();

    @Test
    void parsesTwoPlayers() {
        ArchivePath path = parser.parse(
            "2023/2023-05/2023-05-14-badminton-club/2023-05-14-badminton-club--Ivanov-Petrov.mp4"
        );

        assertTrue(path.valid(), path.getValidationError());
        assertEquals(2023, path.getYear().intValue());
        assertEquals(5, path.getMonth().intValue());
        assertEquals(LocalDate.of(2023, 5, 14), path.getMatchDate());
        assertEquals("badminton-club", path.getClub());
        assertEquals("2023-05-14-badminton-club", path.getSessionName());
        assertFalse(path.isTournament());
        assertEquals(List.of("Ivanov", "Petrov"), path.getPlayers());
    }

    @Test
    void parsesFourPlayersInTournament() {
        ArchivePath path = parser.parse(
            "2023/2023-05/2023-05-20-t-badminton-club/2023-05-20-t-badminton-club--Ivanov-Petrov-Sidorov-Kuznetsov.mp4"
        );

        assertTrue(path.valid(), path.getValidationError());
        assertTrue(path.isTournament());
        assertEquals("badminton-club", path.getClub());
        assertEquals("2023-05-20-t-badminton-club", path.getSessionName());
        assertEquals(4, path.getPlayers().size());
    }

    @Test
    void rejectsFileNameNotMatchingFolder() {
        ArchivePath path = parser.parse(
            "2023/2023-05/2023-05-25-badminton-club/wrong-name-a-b-c.mp4"
        );

        assertFalse(path.valid());
        assertTrue(path.getValidationError().contains("Имя файла не соответствует папке"));
        assertTrue(path.getPlayers().isEmpty());
    }

    @Test
    void rejectsUnexpectedExtension() {
        ArchivePath path = parser.parse(
            "2023/2023-05/2023-05-14-badminton-club/2023-05-14-badminton-club--Ivanov-Petrov.mov"
        );

        assertFalse(path.valid());
        assertTrue(path.getValidationError().contains("Неожиданное расширение файла"));
    }

    @Test
    void rejectsWrongSegmentsCount() {
        ArchivePath path = parser.parse("2023/2023-05/match.mp4");

        assertFalse(path.valid());
        assertTrue(path.getValidationError().contains("сегментов"));
        assertNull(path.getYear());
    }

    @Test
    void rejectsPlayerCountOtherThanTwoOrFour() {
        ArchivePath path = parser.parse(
            "2023/2023-05/2023-05-14-badminton-club/2023-05-14-badminton-club--Ivanov-Petrov-Sidorov.mp4"
        );

        assertFalse(path.valid());
        assertTrue(path.getValidationError().contains("Ожидается 2 или 4 игрока"));
    }

    @Test
    void rejectsYearMismatchInDateFolder() {
        ArchivePath path = parser.parse(
            "2023/2023-05/2022-05-14-badminton-club/2022-05-14-badminton-club--Ivanov-Petrov.mp4"
        );

        assertFalse(path.valid());
        assertTrue(path.getValidationError().contains("не совпадает с годом"));
    }
}
