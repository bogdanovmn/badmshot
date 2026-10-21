package com.github.bogdanovmn.badmshot.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullTextQueryTest {

    @Test
    void emptyForBlankInput() {
        assertTrue(FullTextQuery.of(null).isEmpty());
        assertTrue(FullTextQuery.of("   ").isEmpty());
    }

    @Test
    void buildsPrefixQuery() {
        assertEquals("Ivan:*", FullTextQuery.of("Ivan").orElseThrow());
        assertEquals("Ivan:* & Petrov:*", FullTextQuery.of("Ivan Petrov").orElseThrow());
    }

    @Test
    void stripsPunctuation() {
        assertEquals("Ivan:* & Petrov:*", FullTextQuery.of(" Ivan, Petrov! ").orElseThrow());
    }

    @Test
    void emptyForPunctuationOnly() {
        assertTrue(FullTextQuery.of("!!!").isEmpty());
    }

    @Test
    void normalizesTermForFuzzySearch() {
        assertEquals("", FullTextQuery.normalized(null));
        assertEquals("", FullTextQuery.normalized("   "));
        assertEquals("Ivan Petrov", FullTextQuery.normalized(" Ivan, Petrov! "));
    }
}
