package com.sentinel.common.model;

import java.time.Instant;
import java.util.List;

public record ScanReport(
        String repoUrl,
        String commitSha,
        Instant scannedAt,
        List<Vulnerability> findings
) {
    public List<Vulnerability> findingsAtOrAbove(Severity threshold) {
        return findings.stream()
                .filter(v -> v.severity().isAtLeast(threshold))
                .toList();
    }

}
