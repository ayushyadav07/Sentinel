package com.sentinel.common.dto;

public record RemediationResult(
        RemediationAction actionTaken,
        String prUrl,
        String issueUrl,
        String reason
) {
    public enum RemediationAction { PR_OPENED, ISSUE_OPENED, EMAIL_FALLBACK, SKIPPED_DUPLICATE }
}
