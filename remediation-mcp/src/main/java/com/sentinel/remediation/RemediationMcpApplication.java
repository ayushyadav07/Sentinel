package com.sentinel.remediation;

import com.sentinel.remediation.tool.RemediationTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RemediationMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemediationMcpApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider remediationToolCallbackProvider(RemediationTools remediationTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(remediationTools)
                .build();
    }
}
