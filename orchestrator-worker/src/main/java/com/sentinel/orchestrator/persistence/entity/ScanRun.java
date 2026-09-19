package com.sentinel.orchestrator.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_run")
public class ScanRun {

    @Id
    private UUID jobId;

    @Column(nullable = false)
    private String repoUrl;

    private String commitSha;

    @Column(nullable = false)
    private Instant scannedAt;

    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;

    @Column(nullable = false)
    private boolean actionRequired;

    @Enumerated(EnumType.STRING)
    private RunStatus status;

    @Column(columnDefinition = "TEXT")
    private String remediationAction;

    private String remediationUrl;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(columnDefinition = "TEXT")
    private String agentTranscript;

    protected ScanRun() {
    }

    public ScanRun(UUID jobId, String repoUrl, String commitSha, Instant scannedAt) {
        this.jobId = jobId;
        this.repoUrl = repoUrl;
        this.commitSha = commitSha;
        this.scannedAt = scannedAt;
        this.status = RunStatus.IN_PROGRESS;
    }

    public enum RunStatus { IN_PROGRESS, COMPLETED_CLEAN, COMPLETED_REMEDIATED, FAILED }

    public UUID getJobId() { return jobId; }
    public String getRepoUrl() { return repoUrl; }
    public String getCommitSha() { return commitSha; }
    public Instant getScannedAt() { return scannedAt; }

    public int getCriticalCount() { return criticalCount; }
    public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }

    public int getHighCount() { return highCount; }
    public void setHighCount(int highCount) { this.highCount = highCount; }

    public int getMediumCount() { return mediumCount; }
    public void setMediumCount(int mediumCount) { this.mediumCount = mediumCount; }

    public int getLowCount() { return lowCount; }
    public void setLowCount(int lowCount) { this.lowCount = lowCount; }

    public boolean isActionRequired() { return actionRequired; }
    public void setActionRequired(boolean actionRequired) { this.actionRequired = actionRequired; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }

    public String getRemediationAction() { return remediationAction; }
    public void setRemediationAction(String remediationAction) { this.remediationAction = remediationAction; }

    public String getRemediationUrl() { return remediationUrl; }
    public void setRemediationUrl(String remediationUrl) { this.remediationUrl = remediationUrl; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getAgentTranscript() { return agentTranscript; }
    public void setAgentTranscript(String agentTranscript) { this.agentTranscript = agentTranscript; }
}
