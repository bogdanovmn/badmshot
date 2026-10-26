package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
class ScreenshotShareToken {
    UUID token;
}
