package com.sentinel.scanner;

import com.sentinel.scanner.tool.RepositoryScanTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ScannerMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScannerMcpApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider scanTools(RepositoryScanTools repositoryScanTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(repositoryScanTools)
                .build();
    }
}
