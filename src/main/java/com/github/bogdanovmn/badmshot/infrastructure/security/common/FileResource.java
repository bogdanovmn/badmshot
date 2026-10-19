package com.github.bogdanovmn.badmshot.infrastructure.security.common;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

@RequiredArgsConstructor
public class FileResource {
	public static final String CLASSPATH_PREFIX = "classpath:";

	@NonNull
	private final String fileName;

	public byte[] content() throws IOException {
		return fileName.startsWith(CLASSPATH_PREFIX)
			? internalFileContent()
			: externalFileContent();
	}

	public String contentAsString() throws IOException {
		return new String(content(), StandardCharsets.UTF_8);
	}

	private byte[] externalFileContent() throws IOException {
		return Files.readAllBytes(Paths.get(fileName));
	}

	private byte[] internalFileContent() throws IOException {
		try (
			InputStream file = ClassLoader.getSystemResourceAsStream(
				fileName.replaceFirst(CLASSPATH_PREFIX, "")
			)
		) {
			return file.readAllBytes();
		}
	}
}
