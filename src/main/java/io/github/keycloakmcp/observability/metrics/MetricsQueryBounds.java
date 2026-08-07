package io.github.keycloakmcp.observability.metrics;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

/**
 * Enforces configured metrics query bounds. Does not silently truncate windows.
 */
public final class MetricsQueryBounds {

    private static final Pattern DURATION = Pattern.compile(
            "^\\s*(\\d+)\\s*([smhd])\\s*$", Pattern.CASE_INSENSITIVE);

    private MetricsQueryBounds() {
    }

    public static Duration parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return Duration.ofHours(24);
        }
        Matcher m = DURATION.matcher(raw.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid duration: " + raw);
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "s" -> Duration.ofSeconds(n);
            case "m" -> Duration.ofMinutes(n);
            case "h" -> Duration.ofHours(n);
            case "d" -> Duration.ofDays(n);
            default -> throw new IllegalArgumentException("Invalid duration unit: " + raw);
        };
    }

    public static void validateWindow(MetricWindow window, MetricsConfig config) {
        if (window == null || config == null) {
            return;
        }
        Duration max = parseDuration(config.maxRange());
        Duration requested = Duration.ofSeconds(window.seconds());
        if (requested.compareTo(max) > 0) {
            throw McpException.of(
                    ErrorCode.QUERY_RANGE_EXCEEDED,
                    "Requested metrics window " + window.label()
                            + " exceeds metrics.max-range=" + config.maxRange());
        }
    }

    /**
     * Step for query_range so that the number of points ≈ window/step ≤ maxPoints.
     */
    public static Duration stepFor(MetricWindow window, int maxPoints) {
        int points = Math.max(2, maxPoints);
        long windowSec = Math.max(1, window.seconds());
        long stepSec = Math.max(1, (long) Math.ceil((double) windowSec / (points - 1)));
        return Duration.ofSeconds(stepSec);
    }

    public static boolean exceedsSeriesLimit(int seriesCount, MetricsConfig config) {
        return config != null && seriesCount > Math.max(1, config.maxSeries());
    }

    public static Duration staleAfter(MetricsConfig config) {
        if (config == null) {
            return Duration.ofMinutes(5);
        }
        return parseDuration(config.staleAfter());
    }
}
