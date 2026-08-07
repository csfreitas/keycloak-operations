package io.github.keycloakmcp.domain.platform;

/**
 * How much detail is retained when persisting audit events.
 * <ul>
 *   <li>{@code METADATA} — ids, tool, status, duration only</li>
 *   <li>{@code SANITIZED} — params redacted via SensitiveDataFilter (default)</li>
 *   <li>{@code FULL} — full params (still filtered for known secret keys)</li>
 * </ul>
 */
public enum AuditMode {
    METADATA,
    SANITIZED,
    FULL
}
