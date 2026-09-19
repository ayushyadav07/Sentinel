package com.sentinel.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record TriggerScanRequest(
        @NotBlank String repoUrl,
        String branch,
        String commitSha,
        String installationToken
) {
}
