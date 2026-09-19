package com.sentinel.orchestrator.agent;

import com.sentinel.common.dto.ScanJobMessage;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AgenticRemediationAgent {

    private static final Logger log = LoggerFactory.getLogger(AgenticRemediationAgent.class);

    private static final String SYSTEM_PROMPT = """
            You are Sentinel, an autonomous vulnerability-remediation agent for a single git
            repository. You have access to tools from two systems:

            - scanner tools: scan_repository, get_severity_summary
            - remediation tools: raise_fix_pr, send_email_alert

            Your policy, follow it exactly:
            1. Always start by calling scan_repository on the given repository.
            2. Determine whether any finding has severity CRITICAL or HIGH.
            3. If NO finding is CRITICAL or HIGH: take no further action. Do not call any
               remediation tool.
            4. If at least one finding IS CRITICAL or HIGH: call raise_fix_pr with all of the
               CRITICAL/HIGH findings (not the LOW/MEDIUM ones) as targetFindings, and write a
               short, factual aiSummaryMarkdown rationale yourself based only on the findings you
               actually received -- do not invent CVE details.
            5. Only use send_email_alert if raise_fix_pr's own tool result indicates GitHub
               itself could not be reached -- it is a last resort, not a default reporting channel.
            6. Never call any tool more than once with the same arguments in a single run.

            After you are done, your FINAL message must end with exactly one fenced JSON block
            (```json ... ```) with this shape, reflecting what actually happened:
            {
              "criticalCount": <int>,
              "highCount": <int>,
              "mediumCount": <int>,
              "lowCount": <int>,
              "actionRequired": <true|false>,
              "remediationAction": "<PR_OPENED|ISSUE_OPENED|EMAIL_FALLBACK|SKIPPED_DUPLICATE|null>",
              "remediationUrl": "<url or null>",
              "summary": "<one sentence on what you did and why>"
            }
            Output nothing after that JSON block.
            """;

    private final ChatClient chatClient;

    public AgenticRemediationAgent(ChatClient.Builder chatClientBuilder,
                                   @Qualifier("scannerMcpClient") McpSyncClient scannerMcpClient,
                                   @Qualifier("remediationMcpClient") McpSyncClient remediationMcpClient) {
        var toolCallbackProvider = new SyncMcpToolCallbackProvider(scannerMcpClient, remediationMcpClient);

        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    public String run(ScanJobMessage job) {
        String userPrompt = """
                Repository: %s
                Branch: %s
                Commit SHA: %s
                GitHub token for this repository (pass as installationToken/accessToken to tools
                that need it): %s
                Base branch for any pull request: %s

                Begin.
                """.formatted(
                job.repoUrl(),
                job.branch() != null ? job.branch() : "main",
                job.commitSha() != null ? job.commitSha() : "(not specified, use default branch head)",
                job.installationToken() != null ? job.installationToken() : "(none provided)",
                job.branch() != null ? job.branch() : "main"
        );

        log.info("Agent run starting for job {} ({})", job.jobId(), job.repoUrl());
        String finalResponse = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
        log.info("Agent run finished for job {}", job.jobId());
        return finalResponse;
    }
}
