package com.ironoak.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Settings for the eval outside Spring: environment variable, then system
 * property, then the repo-root .env (the file spring-dotenv reads).
 */
final class EvalEnv {

    private EvalEnv() {
    }

    static String get(String name) {

        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        if (value == null || value.isBlank()) {
            value = fromDotEnv(name);
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String fromDotEnv(String name) {

        Path file = Path.of(".env");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.strip();
                if (trimmed.startsWith(name + "=")) {
                    String value = trimmed.substring(name.length() + 1).strip();
                    if (value.length() >= 2 && (value.startsWith("\"") || value.startsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** Short commit id, with -dirty when the working tree has changes. */
    static String gitCommit() {

        try {
            String head = run("git", "rev-parse", "--short", "HEAD");
            String status = run("git", "status", "--porcelain", "--untracked-files=no");
            return status.isBlank() ? head : head + "-dirty";
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }

    private static String run(String... command) throws IOException, InterruptedException {

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes()).strip();
        process.waitFor();
        return out;
    }
}
