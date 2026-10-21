package com.github.bogdanovmn.badmshot.content;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
class ServedFrame {
    UUID screenshotId;
    UUID matchId;
    UUID clubId;
}
