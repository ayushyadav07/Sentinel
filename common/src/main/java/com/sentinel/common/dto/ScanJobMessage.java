package com.sentinel.common.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ScanJobMessage(
        UUID jobId,
        String repoUrl,
        String branch,
        String commitSha,
        String installationToken,
        String requestedBy,
        Instant enqueuedAt
) implements Serializable {

    public static ScanJobMessage newJob(String repoUrl, String branch, String commitSha,
                                         String installationToken, String requestedBy) {
        return new ScanJobMessage(UUID.randomUUID(), repoUrl, branch, commitSha,
                installationToken, requestedBy, Instant.now());
    }
}
