package com.github.bogdanovmn.badmshot.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
class ScreenshotCommentRequest {
    @NotBlank
    @Size(max = 1000)
    String text;
}
