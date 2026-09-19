package com.sentinel.remediation.github;

import com.sentinel.common.model.Vulnerability;
import org.springframework.stereotype.Component;

@Component
public class PatchClassifier {

    public enum Classification { AUTO_PATCHABLE, NEEDS_HUMAN_REVIEW, NO_FIX_AVAILABLE }

    public Classification classify(Vulnerability vulnerability) {
        if (!vulnerability.hasFix()) {
            return Classification.NO_FIX_AVAILABLE;
        }

        SemVer installed = SemVer.parseOrNull(vulnerability.installedVersion());
        SemVer fixed = SemVer.parseOrNull(vulnerability.fixedVersion());

        if (installed == null || fixed == null) {
            return Classification.NEEDS_HUMAN_REVIEW;
        }

        if (fixed.compareTo(installed) <= 0) {
            return Classification.NEEDS_HUMAN_REVIEW;
        }

        return installed.isSameMajor(fixed)
                ? Classification.AUTO_PATCHABLE
                : Classification.NEEDS_HUMAN_REVIEW;
    }
}
