package io.github.keycloakmcp.service.registry;

import java.util.List;

/** Local checks only: never a registration receipt, connectivity claim or reusable approval. */
public record RegistryPreflightResult(String contractVersion, Status validation, List<Issue> issues,
        boolean persisted, boolean registrationAvailable, String connectivity, String credentialValidity,
        String destinationApproval, boolean globalReadOnly) {
    public enum Status { LOCALLY_VALID, REJECTED, INCONCLUSIVE }
    public record Issue(String field, String code) { }

    public RegistryPreflightResult {
        issues = List.copyOf(issues);
    }

    static RegistryPreflightResult local(Status status, List<Issue> issues, boolean readOnly) {
        return new RegistryPreflightResult("0.1.0", status, issues, false, false,
                "NOT_TESTED", "NOT_TESTED", "NOT_CHECKED", readOnly);
    }
}
