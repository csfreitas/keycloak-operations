package io.github.keycloakmcp.domain.change;

import java.util.List;

/**
 * Typed semantic request for replacing a client's redirect URI and Web Origin sets.
 * A null collection is omitted/unchanged; an empty collection removes every value.
 */
public record ClientUrlChangeRequest(
        String targetId,
        String realm,
        String clientId,
        List<String> redirectUris,
        List<String> webOrigins,
        String actor,
        String idempotencyKey) {
}
