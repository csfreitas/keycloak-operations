package io.github.keycloakmcp.adapter.keycloak;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.methods.HttpRequestBase;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine;
import org.jboss.resteasy.client.jaxrs.engines.ManualClosingApacheHttpClient43Engine;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;

import io.github.keycloakmcp.collection.CollectionBudget;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;

/**
 * Transport used only by scoped read collections. Each token/admin HTTP request
 * receives its own remaining-budget timeouts; shared client settings are never changed.
 * This is cooperative: DNS/TLS and an already-blocked I/O phase are not forcibly cancelled.
 */
final class CollectionAdminTransport {
    private CollectionAdminTransport() { }

    static Client create(String targetId) {
        CollectionDiagnosticPolicy.check();
        ResteasyClientBuilderImpl builder = new ResteasyClientBuilderImpl() {
            @Override
            protected ResteasyClient createResteasyClient(ClientHttpEngine engine,
                    ExecutorService executor, boolean cleanupExecutor,
                    ScheduledExecutorService scheduledExecutor, ClientConfiguration configuration) {
                if (!(engine instanceof ManualClosingApacheHttpClient43Engine apache)) {
                    engine.close();
                    throw new IllegalStateException("Unsupported collection HTTP engine");
                }
                return super.createResteasyClient(new BudgetEngine(apache, targetId), executor,
                        cleanupExecutor, scheduledExecutor, configuration);
            }
        };
        builder.connectionPoolSize(10);
        builder.register(new CollectionJsonProvider(), 100);
        // The standard RESTEasy engine disables Apache decompression. Enable only
        // bounded gzip on this client; the JSON reader caps decoded token bytes too.
        builder.register(new org.jboss.resteasy.plugins.interceptors.GZIPDecodingInterceptor(CollectionJsonProvider.MAX_BYTES));
        builder.register((jakarta.ws.rs.client.ClientResponseFilter) (request, response) -> {
            String encoding = response.getHeaderString(jakarta.ws.rs.core.HttpHeaders.CONTENT_ENCODING);
            if (encoding != null && !encoding.trim().equalsIgnoreCase("gzip")
                    && !encoding.trim().equalsIgnoreCase("identity")) {
                try { response.getEntityStream().close(); } catch (Exception ignored) { }
                throw new jakarta.ws.rs.ProcessingException(CollectionJsonProvider.FAILURE);
            }
            if (encoding != null && encoding.trim().equalsIgnoreCase("gzip")) {
                response.getHeaders().putSingle(jakarta.ws.rs.core.HttpHeaders.CONTENT_ENCODING, "gzip");
            }
        });
        builder.disableAutomaticRetries();
        return builder.build();
    }

    private static final class BudgetEngine extends ApacheHttpClient43Engine {
        private final ManualClosingApacheHttpClient43Engine delegate;
        private final String targetId;
        private final ThreadLocal<HttpRequestBase> activeRequest = new ThreadLocal<>();

        private BudgetEngine(ManualClosingApacheHttpClient43Engine delegate, String targetId) {
            super(delegate.getHttpClient(), false);
            this.delegate = delegate;
            this.targetId = targetId;
            setResponseBufferSize(0);
            setSslContext(delegate.getSslContext());
            setHostnameVerifier(delegate.getHostnameVerifier());
            setFollowRedirects(delegate.isFollowRedirects());
        }

        private CollectionBudget budget() {
            CollectionBudget budget = CollectionBudget.current();
            if (budget == null) throw new IllegalStateException("Collection HTTP client requires an active scope");
            CollectionBudget.checkpoint(targetId);
            CollectionDiagnosticPolicy.check();
            return budget;
        }

        @Override
        protected void loadHttpMethod(ClientInvocation request, HttpRequestBase method) throws Exception {
            activeRequest.set(method);
            budget();
            super.loadHttpMethod(request, method);
            CollectionBudget remaining = budget();
            int millis = (int) Math.max(1, Math.min(5_000,
                    (remaining.remaining().toNanos() + 999_999) / 1_000_000));
            RequestConfig original = method.getConfig();
            RequestConfig.Builder config = original == null ? RequestConfig.custom() : RequestConfig.copy(original);
            config.setConnectTimeout(cap(original == null ? 0 : original.getConnectTimeout(), millis));
            config.setSocketTimeout(cap(original == null ? 0 : original.getSocketTimeout(), millis));
            config.setConnectionRequestTimeout(cap(original == null ? 0 : original.getConnectionRequestTimeout(), millis));
            // OAuth token/Bearer filters own authentication. Native challenge/cookie
            // processing is unnecessary and can log untrusted headers even at WARN.
            config.setAuthenticationEnabled(false);
            config.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
            method.setConfig(config.build());
            remaining.checkpoint();
        }

        private static int cap(int configured, int remaining) {
            return configured > 0 ? Math.min(configured, remaining) : remaining;
        }

        @Override
        public Response invoke(Invocation invocation) {
            budget();
            try {
                Response response = super.invoke(invocation);
                try {
                    budget();
                    if (response.getLength() > CollectionJsonProvider.MAX_BYTES) {
                        throw invalidResponse();
                    }
                    // Initialize the entity wrapper while this request is still associated
                    // with the invocation; body parsing happens after invoke returns.
                    response.hasEntity();
                    return response;
                } catch (RuntimeException failure) {
                    abortRequest();
                    response.close();
                    throw failure;
                }
            } catch (RuntimeException failure) {
                abortRequest();
                budget(); // Timeout/interruption wins over transport diagnostics.
                throw failure;
            } finally {
                activeRequest.remove();
            }
        }

        private void abortRequest() {
            HttpRequestBase request = activeRequest.get();
            if (request != null) request.abort();
        }

        @Override
        protected InputStream createBufferedStream(InputStream input) {
            CollectionBudget captured = budget();
            HttpRequestBase request = activeRequest.get();
            if (request == null) throw new IllegalStateException("Collection HTTP request is unavailable");
            return new FilterInputStream(input) {
                private long received;
                private void check() {
                    try {
                        captured.checkpoint();
                        CollectionDiagnosticPolicy.check();
                    } catch (RuntimeException rejected) { request.abort(); throw rejected; }
                }
                private void count(long size) {
                    if (size > 0) received += size;
                    if (received > CollectionJsonProvider.MAX_BYTES) {
                        request.abort();
                        throw invalidResponse();
                    }
                }
                @Override public int read() throws IOException {
                    check(); int value = in.read(); count(value < 0 ? 0 : 1); check(); return value;
                }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
                    check();
                    if (length == 0) return 0;
                    int allowed = (int) Math.min(length, CollectionJsonProvider.MAX_BYTES - received + 1);
                    int value = in.read(bytes, offset, allowed);
                    count(value); check(); return value;
                }
                @Override public long skip(long length) throws IOException {
                    if (length <= 0) return 0;
                    byte[] bytes = new byte[(int) Math.min(length, 8192)];
                    long skipped = 0;
                    while (skipped < length) {
                        int size = read(bytes, 0, (int) Math.min(bytes.length, length - skipped));
                        if (size < 0) break;
                        skipped += size;
                    }
                    return skipped;
                }
                @Override public boolean markSupported() { return false; }
                @Override public synchronized void mark(int limit) { }
                @Override public synchronized void reset() throws IOException { throw new IOException("Collection stream cannot reset"); }
                @Override public void close() throws IOException {
                    // Never drain an unconsumed response after a timeout/error.
                    request.abort();
                    super.close();
                }
            };
        }

        private static jakarta.ws.rs.ProcessingException invalidResponse() {
            return new jakarta.ws.rs.ProcessingException("Keycloak collection response unavailable or incomplete");
        }

        @Override public void close() {
            try { delegate.close(); } finally { super.close(); }
        }
    }
}
