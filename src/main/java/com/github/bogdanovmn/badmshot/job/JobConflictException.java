package com.github.bogdanovmn.badmshot.job;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
class JobConflictException extends RuntimeException {
    public JobConflictException(String message) {
        super(message);
    }
}
