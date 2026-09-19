package com.sentinel.orchestrator.agent;

public record AgentRunSummary(
        Integer criticalCount,
        Integer highCount,
        Integer mediumCount,
        Integer lowCount,
        Boolean actionRequired,
        String remediationAction,
        String remediationUrl,
        String summary
) {
}
