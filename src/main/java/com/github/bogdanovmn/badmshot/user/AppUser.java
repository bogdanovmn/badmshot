package com.github.bogdanovmn.badmshot.user;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
class AppUser {
    UUID id;
    String displayName;
}
