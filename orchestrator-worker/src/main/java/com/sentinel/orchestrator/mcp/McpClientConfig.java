package com.sentinel.orchestrator.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class McpClientConfig {

    @Value("${sentinel.mcp.scanner-server-url}")
    private String scannerServerUrl;

    @Value("${sentinel.mcp.remediation-server-url}")
    private String remediationServerUrl;

    @Bean(destroyMethod = "close")
    @Qualifier("scannerMcpClient")
    public McpSyncClient scannerMcpClient() {
        return buildClient(scannerServerUrl);
    }

    @Bean(destroyMethod = "close")
    @Qualifier("remediationMcpClient")
    public McpSyncClient remediationMcpClient() {
        return buildClient(remediationServerUrl);
    }

    private McpSyncClient buildClient(String baseUrl) {
        var transport = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint("/sse")
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(600))
                .build();

        int maxAttempts = 10;
        long delayMs = 3000;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                client.initialize();
                return client;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    throw new IllegalStateException(
                            "Could not connect to MCP server at " + baseUrl + " after " + maxAttempts + " attempts", e);
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while retrying MCP connection to " + baseUrl, ie);
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }
}
