package com.github.bogdanovmn.badmshot.infrastructure.web;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
class GlobalExceptionHandling {

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ExceptionResponse> defaultError(HttpServletRequest req, Exception ex) {
        ResponseStatus responseStatus = AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class);
        if (responseStatus != null) {
            return exceptionResponse(req, ex, responseStatus.code().value());
        }

        return exceptionResponse(req, ex, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ExceptionResponse> unauthorizedException(HttpServletRequest req, Exception ex) throws Exception {
        return exceptionResponse(req, ex, HttpStatus.UNAUTHORIZED.value());
    }

    @ExceptionHandler(value = ExpiredJwtException.class)
    public ResponseEntity<ExceptionResponse> jwtValidationException(HttpServletRequest req, Exception ex) throws Exception {
        return exceptionResponse(req, ex, HttpStatus.FORBIDDEN.value());
    }

    @ExceptionHandler(value = {
        NoSuchElementException.class,
        NoResourceFoundException.class
    })
    public ResponseEntity<ExceptionResponse> notFoundException(HttpServletRequest req, Exception ex) throws Exception {
        return exceptionResponse(req, ex, HttpStatus.NOT_FOUND.value());
    }

    @ExceptionHandler(value = {
        BindException.class,
        IllegalArgumentException.class,
        IllegalStateException.class,
        ServletRequestBindingException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ExceptionResponse> badRequest(HttpServletRequest req, Exception ex) throws Exception {
        return exceptionResponse(req, ex, HttpStatus.BAD_REQUEST.value());
    }

    private ResponseEntity<ExceptionResponse> exceptionResponse(HttpServletRequest req, Throwable ex, int statusCode) {
        boolean isServerError = statusCode >= 500;
        if (isServerError) {
            log.error(
                "HTTP Response: {} for [{} {}] processing error: {}",
                statusCode, req.getMethod(), req.getRequestURI(), exceptionMessage(ex), ex
            );
        } else {
            log.warn(
                "HTTP Response: {} for [{} {}] processing error: {}",
                statusCode, req.getMethod(), req.getRequestURI(), exceptionMessage(ex)
            );
        }
        return ResponseEntity.status(statusCode).body(
            ExceptionResponse.builder()
                .message(ex.getMessage())
                .code(statusCode)
                .exception(ex.getClass().getName())
                .stacktrace(
                    isServerError
                        ? Arrays.stream(
                            ex.getStackTrace()
                        ).map(StackTraceElement::toString)
                        .limit(10)
                        .collect(Collectors.toList())
                        : null
                )
                .build()
        );
    }

    private String exceptionMessage(Throwable exception) {
        return Optional.ofNullable(exception.getMessage())
            .orElse(
                Optional.ofNullable(exception.getCause())
                    .map(Throwable::getMessage)
                    .orElse(exception.getClass().getSimpleName())
            );

    }
}
