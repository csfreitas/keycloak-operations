package io.github.keycloakmcp.observability.metrics;

import java.util.Locale;
import java.util.Optional;

/**
 * Controlled metric windows — no arbitrary ranges.
 */
public enum MetricWindow {
    W_1M("1m", 60),
    W_5M("5m", 300),
    W_15M("15m", 900),
    W_30M("30m", 1800),
    W_1H("1h", 3600),
    W_6H("6h", 21600),
    W_24H("24h", 86400);

    private final String label;
    private final int seconds;

    MetricWindow(String label, int seconds) {
        this.label = label;
        this.seconds = seconds;
    }

    public String label() {
        return label;
    }

    public int seconds() {
        return seconds;
    }

    public static MetricWindow defaultWindow() {
        return W_5M;
    }

    public static MetricWindow assessmentDefault() {
        return W_15M;
    }

    public static MetricWindow parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultWindow();
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        for (MetricWindow w : values()) {
            if (w.label.equals(n) || w.name().equalsIgnoreCase(n)) {
                return w;
            }
        }
        throw new IllegalArgumentException("Unsupported metrics window: " + raw
                + " (allowed: 1m,5m,15m,30m,1h,6h,24h)");
    }

    public static Optional<MetricWindow> tryParse(String raw) {
        try {
            return Optional.of(parse(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
