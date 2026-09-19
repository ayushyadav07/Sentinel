package com.sentinel.common.dto;

import com.sentinel.common.model.Vulnerability;

import java.util.List;

public record RemediationRequest(
        String repoUrl,
        String baseBranch,
        String installationToken,
        List<Vulnerability> targetFindings,
        String aiSummaryMarkdown
) {
}
