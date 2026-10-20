package com.github.bogdanovmn.badmshot.catalog;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class CatalogTag {
    UUID id;
    String label;
    long matches;
}
