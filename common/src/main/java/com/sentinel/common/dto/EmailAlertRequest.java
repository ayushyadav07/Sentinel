package com.sentinel.common.dto;

import java.util.List;

public record EmailAlertRequest(
        List<String> recipients,
        String subject,
        String bodyMarkdown
) {
}
