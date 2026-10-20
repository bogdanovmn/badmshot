package com.github.bogdanovmn.badmshot.video;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Builder
@Slf4j
class SystemCommand {
    private final String command;
    private final int maxExecutionTimeInSeconds;
    private final boolean captureOutput;

    public boolean execute() {
        return run().isSuccess();
    }

    public Optional<String> output() {
        return run().getOutput();
    }

    private SystemCommandResult run() {
        Path script = null;
        try {
            script = writeScript(command);
            Process process = new ProcessBuilder(
                isWindows()
                    ? new String[]{"cmd.exe", "/c", script.toString()}
                    : new String[]{"sh", script.toString()}
            ).start();

            String stdout = captureOutput
                ? new BufferedReader(
                    new InputStreamReader(process.getInputStream(), consoleCharset())
                ).lines().collect(Collectors.joining("\n"))
                : null;

            boolean finished = process.waitFor(maxExecutionTimeInSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("Command didn't finish in {} sec. Forced canceling it.", maxExecutionTimeInSeconds);
                process.destroyForcibly();
                return SystemCommandResult.builder().success(false).output(Optional.empty()).build();
            }
            if (process.exitValue() != 0) {
                log.error(
                    "Command error: {}\nDetails: {}",
                    command,
                    new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), consoleCharset())
                    ).lines().collect(Collectors.joining("\n"))
                );
                return SystemCommandResult.builder().success(false).output(Optional.empty()).build();
            }
            return SystemCommandResult.builder()
                .success(true)
                .output(Optional.ofNullable(stdout))
                .build();
        } catch (Exception e) {
            log.error("Run command error: {}\nCommand: {}", e.getMessage(), command);
            return SystemCommandResult.builder().success(false).output(Optional.empty()).build();
        } finally {
            deleteQuietly(script);
        }
    }

    private Path writeScript(String commandLine) throws IOException {
        Path script = Files.createTempFile("badmshot-command", isWindows() ? ".cmd" : ".sh");
        String content = isWindows()
            ? "@echo off\r\n" + commandLine + "\r\n"
            : commandLine + "\n";
        Files.writeString(script, content);
        return script;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Can't delete script {}: {}", path, e.getMessage());
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private Charset consoleCharset() {
        String encoding = System.getProperty("sun.stdout.encoding");
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    @Value
    @Builder
    private static class SystemCommandResult {
        boolean success;
        Optional<String> output;
    }
}
