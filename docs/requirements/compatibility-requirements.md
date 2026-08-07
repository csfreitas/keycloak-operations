# Compatibility requirements

Narrative matrix: [compatibility.md](../compatibility.md).

### COMPAT-001

Keycloak Community and Red Hat build of Keycloak (RHBK) **MUST** be treated as related but not identical products.

### COMPAT-002

Features available only in newer upstream Keycloak **MUST NOT** be assumed present on RHBK without capability or version checks.

### COMPAT-003

Version- or product-specific behavior **SHOULD** use capability / version detection rather than hard-coded version equality alone.

### COMPAT-004

Integrations **MUST** prefer public Admin REST (and other supported public APIs) over Keycloak internal server APIs when the public surface is sufficient.

### COMPAT-005

Documentation **MUST** distinguish versions that were **actually tested** from versions that are only design-compatible.

### COMPAT-006

A version **MUST NOT** be marked as integration-tested when the corresponding IT was skipped or not executed.
