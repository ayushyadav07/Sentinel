package com.sentinel.common.model;

public enum Severity {
    UNKNOWN(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public boolean isAtLeast(Severity other) {
        return this.rank >= other.rank;
    }

    public static Severity fromString(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "CRITICAL" -> CRITICAL;
            case "HIGH" -> HIGH;
            case "MEDIUM" -> MEDIUM;
            case "LOW" -> LOW;
            default -> UNKNOWN;
        };
    }
}