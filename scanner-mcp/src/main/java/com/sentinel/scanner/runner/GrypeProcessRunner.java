package com.sentinel.scanner.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.common.model.Severity;
import com.sentinel.common.model.Vulnerability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class GrypeProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(GrypeProcessRunner.class);

    @Value("${sentinel.scanner.grype-binary:grype}")
    private String grypeBinary;

    @Value("${sentinel.scanner.timeout-seconds:120000}")
    private long timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Runs `grype dir:<path> -o json --file <report>` and parses the result into normalized
     * Vulnerability records.
     */
    public List<Vulnerability> scanFilesystem(Path repoPath) {
        Path reportFile;
        try {
            reportFile = Files.createTempFile("grype-report-", ".json");
        } catch (IOException e) {
            throw new ScanExecutionException("Could not create temp file for Grype report", e);
        }

        List<String> command = List.of(
                grypeBinary, "dir:" + repoPath,
                "-o", "json",
                "--file", reportFile.toString()
        );

        log.info("Running Grype scan: {}", String.join(" ", command));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            log.info("Grype process started, PID={}", process.pid());
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            log.info("Grype process finished={}, exitCode={}",
                    finished,
                    finished ? process.exitValue() : "N/A");
            if (!finished) {
                process.destroyForcibly();
                throw new ScanExecutionException(
                        "Grype scan timed out after " + Duration.ofSeconds(timeoutSeconds), null);
            }

            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new ScanExecutionException(
                        "Grype exited with code " + process.exitValue() + ": " + output, null);
            }
            log.info("Grype scan completed successfully, parsing report from {}", reportFile);
            List<Vulnerability> x = parseReport(reportFile);
            log.info("Grype scan completed with {} findings", x);
            return x;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScanExecutionException("Failed to execute Grype", e);
        } finally {
            try {
                Files.deleteIfExists(reportFile);
            } catch (IOException ignored) {
            }
        }
    }

    private List<Vulnerability> parseReport(Path reportFile) throws IOException {
        List<Vulnerability> findings = new ArrayList<>();
        JsonNode root = objectMapper.readTree(reportFile.toFile());

        JsonNode matches = root.path("matches");
        if (!matches.isArray()) {
            return findings;
        }

        for (JsonNode match : matches) {
            JsonNode vuln = match.path("vulnerability");
            JsonNode artifact = match.path("artifact");

            String fixedVersion = null;
            JsonNode fix = vuln.path("fix");
            if ("fixed".equalsIgnoreCase(fix.path("state").asText(""))
                    && fix.path("versions").isArray() && fix.path("versions").size() > 0) {
                fixedVersion = fix.path("versions").get(0).asText(null);
            }

            String manifestPath = "unknown";
            JsonNode locations = artifact.path("locations");
            if (locations.isArray() && locations.size() > 0) {
                manifestPath = locations.get(0).path("path").asText("unknown");
            }

            String primaryUrl = null;
            JsonNode urls = vuln.path("urls");
            if (urls.isArray() && urls.size() > 0) {
                primaryUrl = urls.get(0).asText(null);
            } else {
                primaryUrl = vuln.path("dataSource").asText(null);
            }

            findings.add(new Vulnerability(
                    vuln.path("id").asText(null),
                    artifact.path("name").asText(null),
                    artifact.path("version").asText(null),
                    fixedVersion,
                    Severity.fromString(vuln.path("severity").asText(null)),
                    extractCvss(vuln.path("cvss")),
                    vuln.path("description").asText(vuln.path("id").asText("")),
                    primaryUrl,
                    manifestPath
            ));
        }
        return findings;
    }

    private double extractCvss(JsonNode cvssArray) {
        if (cvssArray.isArray() && cvssArray.size() > 0) {
            return cvssArray.get(0).path("metrics").path("baseScore").asDouble(0);
        }
        return 0;
    }

    public static class ScanExecutionException extends RuntimeException {
        public ScanExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
