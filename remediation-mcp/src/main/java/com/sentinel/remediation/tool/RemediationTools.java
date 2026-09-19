package com.sentinel.remediation.tool;

import com.sentinel.common.dto.EmailAlertRequest;
import com.sentinel.common.dto.RemediationRequest;
import com.sentinel.common.dto.RemediationResult;
import com.sentinel.common.dto.RemediationResult.RemediationAction;
import com.sentinel.common.model.Vulnerability;
import com.sentinel.remediation.email.EmailAlertSender;
import com.sentinel.remediation.github.DependencyPatcher;
import com.sentinel.remediation.github.GitHubApiClient;
import com.sentinel.remediation.github.PatchClassifier;
import com.sentinel.remediation.github.PatchClassifier.Classification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RemediationTools {

    private static final Logger log = LoggerFactory.getLogger(RemediationTools.class);
    private static final DateTimeFormatter BRANCH_SUFFIX =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final GitHubApiClient gitHub;
    private final DependencyPatcher dependencyPatcher;
    private final PatchClassifier patchClassifier;
    private final EmailAlertSender emailAlertSender;

    public RemediationTools(GitHubApiClient gitHub,
                            DependencyPatcher dependencyPatcher,
                            PatchClassifier patchClassifier,
                            EmailAlertSender emailAlertSender) {
        this.gitHub = gitHub;
        this.dependencyPatcher = dependencyPatcher;
        this.patchClassifier = patchClassifier;
        this.emailAlertSender = emailAlertSender;
    }

    @Tool(name = "raise_fix_pr",
            description = "Given a set of vulnerability findings for a repository, attempts to " +
                    "automatically patch every dependency whose fix is a safe same-major-version " +
                    "bump, and opens a single pull request with those changes. Findings that need " +
                    "a major version bump or have no published fix are instead filed as a tracking " +
                    "GitHub issue so nothing is silently dropped. Returns what action was taken.")
    public RemediationResult raiseFixPr(RemediationRequest request) {
        String[] ownerRepo = parseOwnerRepo(request.repoUrl());
        String owner = ownerRepo[0];
        String repo = ownerRepo[1];
        String token = request.installationToken();
        String baseBranch = request.baseBranch() != null
                ? request.baseBranch()
                : "main";
        List<Vulnerability> autoPatchable = request.targetFindings().stream()
                .filter(v -> patchClassifier.classify(v) == Classification.AUTO_PATCHABLE)
                .toList();

        List<Vulnerability> needsHuman = request.targetFindings().stream()
                .filter(v -> patchClassifier.classify(v) != Classification.AUTO_PATCHABLE)
                .toList();

        String trackingLabel = "sentinel-vuln";

        log.info("Auto-patchable findings: {}", autoPatchable.size());
        log.info("Findings requiring human review: {}", needsHuman.size());
        try {
            if (gitHub.existingOpenItemWithLabel(owner, repo, trackingLabel, token)) {
                return new RemediationResult(
                        RemediationAction.SKIPPED_DUPLICATE,
                        null,
                        null,
                        "An open PR/issue with label '" + trackingLabel + "' already exists for this repo"
                );
            }

            String prUrl = null;

            if (!autoPatchable.isEmpty()) {
                prUrl = openPatchPullRequest(
                        owner,
                        repo,
                        baseBranch,
                        autoPatchable,
                        request.aiSummaryMarkdown(),
                        token
                );
            }

            String issueUrl = null;

            log.info("needsHuman size: {}", needsHuman.size());

            if (!needsHuman.isEmpty()) {
                issueUrl = gitHub.openIssue(
                        owner,
                        repo,
                        "[Sentinel] " + needsHuman.size() + " vulnerabilities need manual review",
                        buildIssueBody(needsHuman, request.aiSummaryMarkdown()),
                        List.of(trackingLabel, "needs-manual-review"),
                        token
                );
            }

            if (prUrl != null) {
                return new RemediationResult(
                        RemediationAction.PR_OPENED,
                        prUrl,
                        issueUrl,
                        "Patched " + autoPatchable.size() + " dependenc(y/ies) automatically"
                );
            } else if (issueUrl != null) {
                return new RemediationResult(
                        RemediationAction.ISSUE_OPENED,
                        null,
                        issueUrl,
                        "No findings were successfully auto-patched; filed a tracking issue instead"
                );
            } else {
                return new RemediationResult(
                        RemediationAction.SKIPPED_DUPLICATE,
                        null,
                        null,
                        "No actionable findings were provided"
                );
            }

        } catch (Exception e) {
            log.error(
                    "GitHub remediation failed for {}/{}, falling back to email",
                    owner,
                    repo,
                    e
            );

            emailAlertSender.send(
                    List.of("security-team@example.com"),
                    "[Sentinel] GitHub remediation failed for " + owner + "/" + repo,
                    "Automatic PR/issue creation failed: " + e.getMessage() +
                            "\n\nFindings:\n" + summarizePlain(request.targetFindings())
            );

            return new RemediationResult(
                    RemediationAction.EMAIL_FALLBACK,
                    null,
                    null,
                    "GitHub API call failed (" + e.getMessage() +
                            "); sent email fallback instead"
            );
        }
    }

    @Tool(name = "send_email_alert",
            description = "Sends an email alert listing vulnerability findings. Use only as a " +
                    "fallback when raise_fix_pr could not act (e.g. GitHub is unreachable) or for " +
                    "informational summaries that don't warrant a PR/issue.")
    public String sendEmailAlert(EmailAlertRequest request) {
        emailAlertSender.send(
                request.recipients(),
                request.subject(),
                request.bodyMarkdown()
        );

        return "Email sent to " + request.recipients().size() + " recipient(s)";
    }

    private String openPatchPullRequest(
            String owner,
            String repo,
            String baseBranch,
            List<Vulnerability> autoPatchable,
            String aiSummary,
            String token) {

        String headSha = gitHub.getDefaultBranchHeadSha(
                owner,
                repo,
                baseBranch,
                token
        );

        String branchName = "sentinel/fix-vulns-" +
                java.time.LocalDateTime.now().format(BRANCH_SUFFIX);

        gitHub.createBranch(
                owner,
                repo,
                branchName,
                headSha,
                token
        );

        int patchedCount = 0;

        for (Vulnerability v : autoPatchable) {
            String reportedManifestPath = normalizeManifestPath(v.manifestPath());
            String patchManifestPath = getPatchManifestPath(reportedManifestPath);

            log.info(
                    "Attempting remediation: reportedManifestPath={}, patchManifestPath={}, " +
                            "pkgName={}, installedVersion={}, fixedVersion={}",
                    reportedManifestPath,
                    patchManifestPath,
                    v.pkgName(),
                    v.installedVersion(),
                    v.fixedVersion()
            );

            GitHubApiClient.FileContent file = gitHub.getFileContent(
                    owner,
                    repo,
                    patchManifestPath,
                    branchName,
                    token
            );

            if (file == null) {
                log.warn(
                        "Could not find patch manifest {} for {}",
                        patchManifestPath,
                        v.pkgName()
                );
                continue;
            }

            var patched = patchManifest(
                    patchManifestPath,
                    file.content(),
                    v
            );

            if (patched.isPresent()) {

                gitHub.updateFile(
                        owner,
                        repo,
                        patchManifestPath,
                        patched.get(),
                        file.sha(),
                        branchName,
                        "fix(deps): bump " + v.pkgName() +
                                " to " + v.fixedVersion() +
                                " (" + v.cveId() + ")",
                        token
                );

                patchedCount++;

                log.info(
                        "Successfully patched {} from {} to {} in {}",
                        v.pkgName(),
                        v.installedVersion(),
                        v.fixedVersion(),
                        patchManifestPath
                );

            } else {
                log.warn(
                        "Patch classified as safe but dependency could not be located in {}: {} {} -> {}",
                        patchManifestPath,
                        v.pkgName(),
                        v.installedVersion(),
                        v.fixedVersion()
                );
            }
        }

        if (patchedCount == 0) {
            return null;
        }

        String title = "[Sentinel] Fix " +
                patchedCount +
                " vulnerable dependenc" +
                (patchedCount == 1 ? "y" : "ies");

        return gitHub.openPullRequest(
                owner,
                repo,
                title,
                buildPrBody(autoPatchable, aiSummary),
                branchName,
                baseBranch,
                List.of("sentinel-vuln", "security", "automated-pr"),
                token
        );
    }

    private java.util.Optional<String> patchManifest(
            String manifestPath,
            String content,
            Vulnerability vulnerability) {

        String normalizedPath = normalizeManifestPath(manifestPath);

        return switch (normalizedPath) {

            case "pom.xml" ->
                    dependencyPatcher.patchMavenPom(
                            content,
                            vulnerability
                    );

            case "build.gradle", "build.gradle.kts" ->
                    dependencyPatcher.patchGradleBuild(
                            content,
                            vulnerability
                    );

            case "requirements.txt" ->
                    dependencyPatcher.patchRequirementsTxt(
                            content,
                            vulnerability
                    );

            case "package.json" ->
                    dependencyPatcher.patchPackageJson(
                            content,
                            vulnerability
                    );

            case "go.mod" ->
                    dependencyPatcher.patchGoMod(
                            content,
                            vulnerability
                    );

            case "Gemfile" ->
                    dependencyPatcher.patchGemfile(
                            content,
                            vulnerability
                    );

            case "Gemfile.lock" ->
                    dependencyPatcher.patchGemfileLock(content, vulnerability);

            case "composer.json" ->
                    dependencyPatcher.patchComposerJson(
                            content,
                            vulnerability
                    );

            case "composer.lock" ->
                    dependencyPatcher.patchComposerLock(
                            content,
                            vulnerability
                    );

            default ->
                    java.util.Optional.empty();
        };
    }

    private String normalizeManifestPath(String manifestPath) {
        if (manifestPath == null || manifestPath.isBlank()) {
            return manifestPath;
        }

        return manifestPath.replaceFirst("^/+", "");
    }

    private String getPatchManifestPath(String reportedManifestPath) {

        String normalizedPath = normalizeManifestPath(reportedManifestPath);

        if ("package-lock.json".equals(normalizedPath)) {
            return "package.json";
        }

        return normalizedPath;
    }

    private String buildPrBody(
            List<Vulnerability> findings,
            String aiSummary) {

        StringBuilder sb = new StringBuilder();

        sb.append("## Automated dependency fix\n\n");

        sb.append("Opened automatically by the Sentinel remediation agent after detecting the ")
                .append("following vulnerabilities with a safe (same-major-version) fix available.\n\n");

        sb.append("| CVE | Package | Installed | Fixed | Severity |\n")
                .append("|---|---|---|---|---|\n");

        for (Vulnerability v : findings) {
            sb.append("| ")
                    .append(v.cveId())
                    .append(" | ")
                    .append(v.pkgName())
                    .append(" | ")
                    .append(v.installedVersion())
                    .append(" | ")
                    .append(v.fixedVersion())
                    .append(" | ")
                    .append(v.severity())
                    .append(" |\n");
        }

        if (aiSummary != null && !aiSummary.isBlank()) {
            sb.append("\n### Summary\n\n")
                    .append(aiSummary)
                    .append("\n");
        }

        sb.append("\n---\n")
                .append("_This PR only touches dependency version declarations. ")
                .append("Please review CI results before merging._");

        return sb.toString();
    }

    private String buildIssueBody(
            List<Vulnerability> findings,
            String aiSummary) {

        StringBuilder sb = new StringBuilder();

        sb.append("## Vulnerabilities requiring manual review\n\n");

        sb.append("These findings were **not** auto-patched because the fix requires a major ")
                .append("version bump, the version could not be parsed, or no fix is published yet.\n\n");

        sb.append("| CVE | Package | Installed | Fixed | Severity | Reason |\n")
                .append("|---|---|---|---|---|---|\n");

        for (Vulnerability v : findings) {
            sb.append("| ")
                    .append(v.cveId())
                    .append(" | ")
                    .append(v.pkgName())
                    .append(" | ")
                    .append(v.installedVersion())
                    .append(" | ")
                    .append(v.hasFix()
                            ? v.fixedVersion()
                            : "_none published_")
                    .append(" | ")
                    .append(v.severity())
                    .append(" | ")
                    .append(v.hasFix()
                            ? "major version bump"
                            : "no fix available")
                    .append(" |\n");
        }

        if (aiSummary != null && !aiSummary.isBlank()) {
            sb.append("\n### Summary\n\n")
                    .append(aiSummary)
                    .append("\n");
        }

        return sb.toString();
    }

    private String summarizePlain(List<Vulnerability> findings) {
        StringBuilder sb = new StringBuilder();

        for (Vulnerability v : findings) {
            sb.append("- ")
                    .append(v.cveId())
                    .append(" ")
                    .append(v.pkgName())
                    .append(" (")
                    .append(v.severity())
                    .append(")\n");
        }

        return sb.toString();
    }

    private String[] parseOwnerRepo(String repoUrl) {
        URI uri = URI.create(repoUrl);

        String path = uri.getPath()
                .replaceFirst("^/", "")
                .replaceFirst("\\.git$", "");

        String[] parts = path.split("/");

        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Cannot parse owner/repo from " + repoUrl
            );
        }

        return new String[]{parts[0], parts[1]};
    }
}