package com.github.bogdanovmn.badmshot.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class ExceptionResponse {
	String message;
	int code;
	String exception;
	List<String> stacktrace;

}
