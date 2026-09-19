package com.sentinel.orchestrator.gating;

import com.sentinel.common.model.ScanReport;
import com.sentinel.common.model.Severity;
import com.sentinel.common.model.Vulnerability;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SeverityGate {

    private static final Severity ACTION_THRESHOLD = Severity.HIGH;

    public boolean requiresAction(ScanReport report) {
        return !findActionableFindings(report).isEmpty();
    }

    public List<Vulnerability> findActionableFindings(ScanReport report) {
        return report.findingsAtOrAbove(ACTION_THRESHOLD);
    }
}
