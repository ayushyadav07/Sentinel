package com.sentinel.remediation.github;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    private static final Pattern PATTERN = Pattern.compile("^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?.*$");
    public static SemVer parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = PATTERN.matcher(raw.trim());
        if (!m.matches()) {
            return null;
        }
        int major = Integer.parseInt(m.group(1));
        int minor = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return new SemVer(major, minor, patch);
    }

    @Override
    public int compareTo(SemVer other) {
        if (this.major != other.major) return Integer.compare(this.major, other.major);
        if (this.minor != other.minor) return Integer.compare(this.minor, other.minor);
        return Integer.compare(this.patch, other.patch);
    }

    public boolean isSameMajor(SemVer other) {
        return this.major == other.major;
    }
}
