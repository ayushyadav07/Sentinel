package com.sentinel.scanner.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RepositoryFetcher {

    private static final Logger log = LoggerFactory.getLogger(RepositoryFetcher.class);

    public Path shallowClone(String repoUrl, String branch, String commitSha, String token) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("scan-repo-");
        } catch (IOException e) {
            throw new GrypeProcessRunner.ScanExecutionException("Could not create scratch dir", e);
        }

        String cloneUrl = injectToken(repoUrl, token);
        List<String> command = List.of(
                "git", "clone", "--depth", "1",
                "--branch", branch != null ? branch : "main",
                cloneUrl, workDir.toString()
        );

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new GrypeProcessRunner.ScanExecutionException(
                        "git clone failed: " + output, null);
            }
            if (commitSha != null && !commitSha.isBlank()) {
                checkoutCommit(workDir, commitSha);
            }
            return workDir;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GrypeProcessRunner.ScanExecutionException("Failed to clone repository", e);
        }
    }

    private void checkoutCommit(Path workDir, String commitSha) throws IOException, InterruptedException {
        new ProcessBuilder("git", "fetch", "--depth", "1", "origin", commitSha)
                .directory(workDir.toFile()).redirectErrorStream(true).start().waitFor(60, TimeUnit.SECONDS);
        new ProcessBuilder("git", "checkout", commitSha)
                .directory(workDir.toFile()).redirectErrorStream(true).start().waitFor(30, TimeUnit.SECONDS);
    }

    private String injectToken(String repoUrl, String token) {
        if (token == null || token.isBlank() || !repoUrl.startsWith("https://")) {
            return repoUrl;
        }
        return repoUrl.replaceFirst("https://", "https://x-access-token:" + token + "@");
    }

    public void cleanup(Path workDir) {
        try (var walk = Files.walk(workDir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean up scan workspace {}: {}", workDir, e.getMessage());
        }
    }
}
