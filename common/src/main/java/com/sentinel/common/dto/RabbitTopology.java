package com.sentinel.common.dto;

public final class RabbitTopology {

    private RabbitTopology() {}

    public static final String SCAN_JOBS_EXCHANGE = "sentinel.scan.exchange";
    public static final String SCAN_JOBS_QUEUE = "sentinel.scan.jobs.queue";
    public static final String SCAN_JOBS_ROUTING_KEY = "scan.jobs";

    public static final String DLX_EXCHANGE = "sentinel.scan.dlx";
    public static final String SCAN_JOBS_DLQ = "sentinel.scan.jobs.dlq";
    public static final String DLQ_ROUTING_KEY = "scan.jobs.dead";

    public static final int RETRY_DELAY_MS = 30_000;
    public static final int MAX_RETRY_ATTEMPTS = 3;
}
