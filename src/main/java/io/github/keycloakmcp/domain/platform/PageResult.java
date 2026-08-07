package io.github.keycloakmcp.domain.platform;

import java.util.List;

/**
 * Paginated result for REST and service queries.
 *
 * @param page 0-based page index
 * @param size page size (clamped 1–100)
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    public static int clampPage(int page) {
        return Math.max(page, 0);
    }
}
