# Example prompts

Prompts for an MCP-capable agent connected to `keycloak-operations-mcp`.

Always resolve the environment with `keycloak_list_targets` first when needed.
Pass `targetId` on every admin call.

## English

### Targets

> List the available Keycloak environments.

> Show metadata for target `lab-keycloak-a` (do not show secrets or URLs).

### Inventory

> List all realms on target `lab-keycloak-a` and summarize which ones are enabled.

> Show server info for `lab-keycloak-a`: product, version, and capabilities.

> On target `lab-keycloak-a`, in realm `mcp-demo`, list clients and highlight
> public vs confidential. Do not ask for or display client secrets.

> On target `lab-keycloak-b`, list realms (expect `company-b` when multi-target lab is up).

### Users and groups

> On `lab-keycloak-a`, search users named alice in `mcp-demo`.

### Assessment (architecture-ready)

> Explain finding `KC-OCP-HA-001` if `deployment.replicas` is 1 for target `customer-a-prd`.

> Compare HA posture of `customer-a-hml` vs `customer-a-prd` (future `keycloak_compare_targets`).

## Português

### Targets

> Liste os ambientes disponíveis.

> Mostre os metadados do target `lab-keycloak-a` (sem secrets nem URLs).

### Inventário

> Liste os realms do ambiente `lab-keycloak-a`.

> Mostre o client `portal-web` do realm `mcp-demo` em `lab-keycloak-a`.

> Liste os realms do `lab-keycloak-b` (lab multi-target).
