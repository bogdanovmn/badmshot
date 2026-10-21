package com.github.bogdanovmn.badmshot.common;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class FullTextQuery {

    private FullTextQuery() {
    }

    public static Optional<String> of(String term) {
        String normalized = normalized(term);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String query = Arrays.stream(normalized.split("\\s+"))
            .map(word -> word + ":*")
            .collect(Collectors.joining(" & "));
        return Optional.of(query);
    }

    public static String normalized(String term) {
        if (term == null || term.isBlank()) {
            return "";
        }
        return Arrays.stream(term.trim().split("\\s+"))
            .map(word -> word.replaceAll("[^\\p{L}\\p{N}]", ""))
            .filter(word -> !word.isEmpty())
            .collect(Collectors.joining(" "));
    }
}
