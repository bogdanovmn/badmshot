package com.github.bogdanovmn.badmshot.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;

import java.util.Optional;

@RequiredArgsConstructor
class HttpRequest {
	private final HttpServletRequest request;

	public Optional<String> authToken() {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		return (header != null && header.startsWith("Bearer "))
			? Optional.of(
				header.split(" ")[1].trim()
			)
			: Optional.empty();
	}
}
