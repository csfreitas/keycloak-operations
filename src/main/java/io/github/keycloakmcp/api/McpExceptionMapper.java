package io.github.keycloakmcp.api;

import java.util.Map;

import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class McpExceptionMapper implements ExceptionMapper<McpException> {

    @Override
    public Response toResponse(McpException exception) {
        ErrorCode code = exception.getCode();
        int status = switch (code) {
            case REALM_NOT_FOUND, CLIENT_NOT_FOUND, USER_NOT_FOUND, GROUP_NOT_FOUND, ROLE_NOT_FOUND,
                    TARGET_NOT_FOUND -> 404;
            case INVALID_ARGUMENT -> 400;
            case AUTHENTICATION_FAILED -> 401;
            case AUTHORIZATION_FAILED, TARGET_NOT_AUTHORIZED, TARGET_DISABLED -> 403;
            case KEYCLOAK_UNAVAILABLE, OPENSHIFT_UNAVAILABLE, KUBERNETES_UNAVAILABLE, TARGET_UNAVAILABLE -> 503;
            case UNSUPPORTED_CAPABILITY -> 501;
            default -> 500;
        };
        return Response.status(status)
                .entity(Map.of(
                        "code", code.name(),
                        "message", exception.getMessage() == null ? code.name() : exception.getMessage()))
                .build();
    }
}
