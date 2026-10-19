package com.github.bogdanovmn.badmshot.common;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class FullTextQuery {

    private FullTextQuery() {
    }

    public static Optional<String> of(String term) {
        if (term == null || term.isBlank()) {
            return Optional.empty();
        }
        String query = Arrays.stream(term.trim().split("\\s+"))
            .map(word -> word.replaceAll("[^\\p{L}\\p{N}]", ""))
            .filter(word -> !word.isEmpty())
            .map(word -> word + ":*")
            .collect(Collectors.joining(" & "));
        return query.isEmpty() ? Optional.empty() : Optional.of(query);
    }
}
