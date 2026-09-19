package com.sentinel.scanner.tool;

import com.sentinel.common.model.ScanReport;
import com.sentinel.common.model.Severity;
import com.sentinel.common.model.Vulnerability;
import com.sentinel.scanner.runner.GrypeProcessRunner;
import com.sentinel.scanner.runner.RepositoryFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class RepositoryScanTools {

    private static final Logger log = LoggerFactory.getLogger(RepositoryScanTools.class);

    private final RepositoryFetcher repositoryFetcher;
    private final GrypeProcessRunner grypeProcessRunner;

    public RepositoryScanTools(RepositoryFetcher repositoryFetcher, GrypeProcessRunner grypeProcessRunner) {
        this.repositoryFetcher = repositoryFetcher;
        this.grypeProcessRunner = grypeProcessRunner;
    }

    @Tool(name = "scan_repository",
            description = "Clones the given git repository and runs a dependency vulnerability scan. " +
                    "Returns a structured report of all findings (CVE id, package, installed/fixed " +
                    "version, severity, CVSS score). Use this as the first step before deciding on " +
                    "remediation action.")
    public ScanReport scanRepository(
            @ToolParam(description = "HTTPS git URL of the repository to scan") String repoUrl,
            @ToolParam(description = "Branch to scan, defaults to main/master", required = false) String branch,
            @ToolParam(description = "Optional exact commit SHA to scan", required = false) String commitSha,
            @ToolParam(description = "Optional GitHub token for cloning private repos", required = false) String accessToken
    ) {
        log.info("scan_repository invoked for {} (branch={}, commit={})", repoUrl, branch, commitSha);
        Path repoPath = repositoryFetcher.shallowClone(repoUrl, branch, commitSha, accessToken);
        try {
            List<Vulnerability> findings = grypeProcessRunner.scanFilesystem(repoPath);
            return new ScanReport(repoUrl, commitSha, Instant.now(), findings);
        } finally {
            repositoryFetcher.cleanup(repoPath);
        }
    }

    @Tool(name = "get_severity_summary",
            description = "Given a previously produced scan report's findings, returns a count of " +
                    "vulnerabilities per severity level (CRITICAL/HIGH/MEDIUM/LOW) plus a boolean " +
                    "flag indicating whether the CRITICAL+HIGH threshold for remediation is met.")
    public Map<String, Object> getSeveritySummary(
            @ToolParam(description = "List of findings from a scan_repository call") List<Vulnerability> findings
    ) {
        Map<Severity, Long> counts = findings.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Vulnerability::severity, java.util.stream.Collectors.counting()));

        long criticalOrHigh = counts.getOrDefault(Severity.CRITICAL, 0L) + counts.getOrDefault(Severity.HIGH, 0L);

        return Map.of(
                "critical", counts.getOrDefault(Severity.CRITICAL, 0L),
                "high", counts.getOrDefault(Severity.HIGH, 0L),
                "medium", counts.getOrDefault(Severity.MEDIUM, 0L),
                "low", counts.getOrDefault(Severity.LOW, 0L),
                "actionRequired", criticalOrHigh > 0
        );
    }
}