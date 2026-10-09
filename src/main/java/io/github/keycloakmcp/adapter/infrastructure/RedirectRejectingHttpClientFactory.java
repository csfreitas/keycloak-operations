package io.github.keycloakmcp.adapter.infrastructure;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.Interceptor;
import io.fabric8.kubernetes.client.http.TlsVersion;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;

/** Keeps Fabric8's configured TLS/authentication while never enabling automatic redirects. */
public final class RedirectRejectingHttpClientFactory implements HttpClient.Factory {
    private final HttpClient.Factory delegate;

    public RedirectRejectingHttpClientFactory() { this(HttpClientUtils.getHttpClientFactory()); }

    RedirectRejectingHttpClientFactory(HttpClient.Factory delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate);
    }

    @Override public HttpClient.Builder newBuilder() {
        // The unconfigured builder starts with redirects disabled. The inherited
        // Factory.newBuilder(Config) still applies every other common option.
        return new Builder(delegate.newBuilder());
    }

    private static final class Builder implements HttpClient.Builder {
        private final HttpClient.Builder delegate;
        private Builder(HttpClient.Builder delegate) { this.delegate = delegate; }
        @Override public HttpClient build() { return delegate.build(); }
        @Override public Builder followAllRedirects() { return this; }
        @Override public Builder connectTimeout(long value, TimeUnit unit) { delegate.connectTimeout(value, unit); return this; }
        @Override public Builder addOrReplaceInterceptor(String name, Interceptor interceptor) { delegate.addOrReplaceInterceptor(name, interceptor); return this; }
        @Override public Builder authenticatorNone() { delegate.authenticatorNone(); return this; }
        @Override public Builder tag(Object value) { delegate.tag(value); return this; }
        @Override public Builder sslContext(KeyManager[] keys, TrustManager[] trusts) { delegate.sslContext(keys, trusts); return this; }
        @Override public Builder proxyAddress(InetSocketAddress value) { delegate.proxyAddress(value); return this; }
        @Override public Builder proxyAuthorization(String value) { delegate.proxyAuthorization(value); return this; }
        @Override public Builder tlsVersions(TlsVersion... values) { delegate.tlsVersions(values); return this; }
        @Override public Builder tlsServerName(String value) { delegate.tlsServerName(value); return this; }
        @Override public Builder preferHttp11() { delegate.preferHttp11(); return this; }
        @Override public Builder proxyType(HttpClient.ProxyType value) { delegate.proxyType(value); return this; }
    }
}
