package com.github.bogdanovmn.badmshot.content;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
class ScreenshotAccessDeniedException extends RuntimeException {
    ScreenshotAccessDeniedException(String message) {
        super(message);
    }
}
