package io.github.keycloakmcp.domain.change;

import java.util.List;
import java.util.Objects;

public record ChangeVerificationResult(
        boolean verified,
        String status,
        String message,
        List<ChangeDiffEntry> mismatches) {

    public ChangeVerificationResult {
        Objects.requireNonNull(status, "status");
        mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
    }

    public static ChangeVerificationResult verified(String message) {
        return new ChangeVerificationResult(true, "VERIFIED", message, List.of());
    }

    public static ChangeVerificationResult failed(String message, List<ChangeDiffEntry> mismatches) {
        return new ChangeVerificationResult(false, "VERIFICATION_FAILED", message, mismatches);
    }
}
