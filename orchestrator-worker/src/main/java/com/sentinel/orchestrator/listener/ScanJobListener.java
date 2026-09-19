package com.sentinel.orchestrator.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.sentinel.common.dto.ScanJobMessage;
import com.sentinel.common.model.ScanReport;
import com.sentinel.orchestrator.agent.AgentRunSummary;
import com.sentinel.orchestrator.agent.AgenticRemediationAgent;
import com.sentinel.orchestrator.gating.SeverityGate;
import com.sentinel.orchestrator.persistence.entity.ScanRun;
import com.sentinel.orchestrator.persistence.repository.ScanRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ScanJobListener implements ChannelAwareMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ScanJobListener.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final MessageConverter messageConverter;
    private final SeverityGate severityGate;
    private final ScanRunRepository scanRunRepository;
    private final AgenticRemediationAgent agenticRemediationAgent;

    public ScanJobListener(MessageConverter messageConverter,
                            SeverityGate severityGate,
                            ScanRunRepository scanRunRepository,
                           AgenticRemediationAgent agenticRemediationAgent) {
        this.messageConverter = messageConverter;
        this.severityGate = severityGate;
        this.scanRunRepository = scanRunRepository;
        this.agenticRemediationAgent = agenticRemediationAgent;
    }

    @Override
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        ScanJobMessage job = (ScanJobMessage) messageConverter.fromMessage(message);

        try {
            processJobAgentically(job);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed processing scan job {} for {}: {}", job.jobId(), job.repoUrl(), e.getMessage(), e);
            persistFailure(job, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void processJobAgentically(ScanJobMessage job) {
        log.info("Processing scan job {} for {} (agentic)", job.jobId(), job.repoUrl());

        String agentOutput = agenticRemediationAgent.run(job);
        AgentRunSummary parsed = parseAgentSummary(agentOutput);

        ScanRun run = new ScanRun(job.jobId(), job.repoUrl(), job.commitSha(), java.time.Instant.now());
        run.setAgentTranscript(agentOutput);

        if (parsed == null) {
            log.warn("Job {}: could not parse a JSON summary from the agent's response; " +
                    "storing raw transcript only", job.jobId());
            run.setStatus(ScanRun.RunStatus.COMPLETED_CLEAN);
            scanRunRepository.save(run);
            return;
        }

        run.setCriticalCount(nullToZero(parsed.criticalCount()));
        run.setHighCount(nullToZero(parsed.highCount()));
        run.setMediumCount(nullToZero(parsed.mediumCount()));
        run.setLowCount(nullToZero(parsed.lowCount()));
        run.setActionRequired(Boolean.TRUE.equals(parsed.actionRequired()));
        run.setRemediationAction(parsed.remediationAction());
        run.setRemediationUrl(parsed.remediationUrl());
        run.setStatus(run.isActionRequired()
                ? ScanRun.RunStatus.COMPLETED_REMEDIATED
                : ScanRun.RunStatus.COMPLETED_CLEAN);

        scanRunRepository.save(run);
        log.info("Job {} complete (agentic): {}", job.jobId(), parsed.summary());
    }

    private AgentRunSummary parseAgentSummary(String agentOutput) {
        if (agentOutput == null) {
            return null;
        }
        Matcher matcher = JSON_BLOCK.matcher(agentOutput);
        String jsonCandidate = matcher.find() ? matcher.group(1) : agentOutput.trim();
        try {
            return JSON_MAPPER.readValue(jsonCandidate, AgentRunSummary.class);
        } catch (Exception e) {
            log.warn("Failed to parse agent summary JSON: {}", e.getMessage());
            return null;
        }
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    private void tallyCounts(ScanRun run, ScanReport report) {
        var findings = report.findings();
        run.setCriticalCount((int) findings.stream()
                .filter(v -> v.severity() == com.sentinel.common.model.Severity.CRITICAL).count());
        run.setHighCount((int) findings.stream()
                .filter(v -> v.severity() == com.sentinel.common.model.Severity.HIGH).count());
        run.setMediumCount((int) findings.stream()
                .filter(v -> v.severity() == com.sentinel.common.model.Severity.MEDIUM).count());
        run.setLowCount((int) findings.stream()
                .filter(v -> v.severity() == com.sentinel.common.model.Severity.LOW).count());
    }

    private void persistFailure(ScanJobMessage job, Exception e) {
        try {
            ScanRun run = new ScanRun(job.jobId(), job.repoUrl(), job.commitSha(), java.time.Instant.now());
            run.setStatus(ScanRun.RunStatus.FAILED);
            run.setFailureReason(e.getMessage());
            scanRunRepository.save(run);
        } catch (Exception persistFailure) {
            log.error("Additionally failed to persist failure record for job {}", job.jobId(), persistFailure);
        }
    }
}
