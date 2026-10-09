package io.github.keycloakmcp.adapter.keycloak;

import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.logging.LogFactory;
import org.jboss.logging.Logger;

import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

/**
 * Admission check for the fixed scoped-read transport, not a global log filter.
 * RESTEasy can log a raw transport cause before its caller can sanitize it;
 * Apache debug channels can log headers, bytes, invalid protocol text and URLs.
 * The check deliberately rejects collection instead of changing operator logging.
 *
 * Recheck at construction and request/body boundaries. Operator reconfiguration
 * during already-running I/O is not atomic with these checks. Keep this list in
 * step with the selected RESTEasy/Apache engine when dependencies change.
 */
final class CollectionDiagnosticPolicy {
    private static final List<String> CATEGORIES = List.of(
            "org.jboss.resteasy.client.jaxrs.i18n",
            "org.jboss.resteasy.resteasy_jaxrs.i18n",
            "org.apache.http.wire",
            "org.apache.http.headers",
            "org.apache.http.impl.conn.DefaultManagedHttpClientConnection",
            "org.apache.http.impl.conn.DefaultHttpResponseParser",
            "org.apache.http.impl.conn.DefaultHttpClientConnectionOperator",
            "org.apache.http.impl.conn.PoolingHttpClientConnectionManager",
            "org.apache.http.impl.conn.BasicHttpClientConnectionManager",
            "org.apache.http.impl.conn.CPool",
            "org.apache.http.impl.execchain.MainClientExec",
            "org.apache.http.impl.execchain.ProtocolExec",
            "org.apache.http.impl.execchain.RedirectExec",
            "org.apache.http.impl.execchain.RetryExec",
            "org.apache.http.impl.execchain.ServiceUnavailableRetryExec",
            "org.apache.http.impl.client.InternalHttpClient",
            "org.apache.http.impl.client.TargetAuthenticationStrategy",
            "org.apache.http.impl.client.ProxyAuthenticationStrategy",
            "org.apache.http.impl.client.BasicAuthCache",
            "org.apache.http.impl.auth.HttpAuthenticator",
            "org.apache.http.client.protocol.RequestAddCookies",
            "org.apache.http.client.protocol.ResponseProcessCookies",
            "org.apache.http.client.protocol.RequestAuthCache",
            "org.apache.http.client.protocol.ResponseAuthCache",
            "org.apache.http.client.protocol.RequestClientConnControl",
            "org.apache.http.conn.ssl.SSLConnectionSocketFactory",
            "org.apache.http.conn.ssl.DefaultHostnameVerifier",
            "org.apache.http.conn.util.PublicSuffixMatcherLoader",
            // Apache's SSL logger uses getClass(), including these engine subclasses.
            "org.jboss.resteasy.client.jaxrs.engines.ClientHttpEngineBuilder43$1",
            "org.jboss.resteasy.client.jaxrs.engines.ClientHttpEngineBuilder43$2",
            "org.jboss.resteasy.client.jaxrs.engines.ClientHttpEngineBuilder43$3");

    private CollectionDiagnosticPolicy() { }

    static void check() {
        check(category -> category.startsWith("org.jboss.resteasy.") && !category.contains("ClientHttpEngineBuilder43$")
                ? Logger.getLogger(category).isDebugEnabled()
                : LogFactory.getLog(category).isDebugEnabled());
    }

    static void check(Predicate<String> diagnosticsEnabled) {
        final boolean unsafe;
        try {
            unsafe = CATEGORIES.stream().anyMatch(diagnosticsEnabled);
        } catch (RuntimeException ignored) {
            throw rejection();
        }
        if (unsafe) throw rejection();
    }

    static List<String> diagnosticCategories() {
        return CATEGORIES;
    }

    private static McpException rejection() {
        return McpException.of(ErrorCode.EVIDENCE_COLLECTION_FAILED,
                "Scoped Admin collection requires safe transport diagnostics");
    }
}
