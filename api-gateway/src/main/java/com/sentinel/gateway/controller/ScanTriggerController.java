package com.sentinel.gateway.controller;

import com.sentinel.common.dto.RabbitTopology;
import com.sentinel.common.dto.ScanJobMessage;
import com.sentinel.gateway.dto.TriggerScanRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ScanTriggerController {

    private static final Logger log = LoggerFactory.getLogger(ScanTriggerController.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${sentinel.gateway.default-branch:main}")
    private String defaultBranch;

    public ScanTriggerController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/scans/trigger")
    public ResponseEntity<Map<String, Object>> triggerManualScan(@Valid @RequestBody TriggerScanRequest request) {
        ScanJobMessage job = ScanJobMessage.newJob(
                request.repoUrl(),
                request.branch() != null ? request.branch() : defaultBranch,
                request.commitSha(),
                request.installationToken(),
                "manual-trigger"
        );
        publish(job);
        return ResponseEntity.accepted().body(Map.of("jobId", job.jobId(), "status", "queued"));
    }

    private void publish(ScanJobMessage job) {
        log.info("Publishing scan job {} for {}", job.jobId(), job.repoUrl());
        rabbitTemplate.convertAndSend(
                RabbitTopology.SCAN_JOBS_EXCHANGE, RabbitTopology.SCAN_JOBS_ROUTING_KEY, job);
    }
}
