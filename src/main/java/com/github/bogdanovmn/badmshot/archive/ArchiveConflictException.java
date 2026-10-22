package com.github.bogdanovmn.badmshot.archive;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
class ArchiveConflictException extends RuntimeException {
    ArchiveConflictException(String message) {
        super(message);
    }
}
