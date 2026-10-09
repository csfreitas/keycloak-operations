package io.github.keycloakmcp.adapter.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.Interceptor;
import io.fabric8.kubernetes.client.http.TlsVersion;

class RedirectRejectingHttpClientFactoryTest {
    @Test void commonConfigurationKeepsAuthenticationAndTlsButNeverEnablesRedirects() {
        HttpClient.Factory delegate = mock(HttpClient.Factory.class);
        HttpClient.Builder builder = mock(HttpClient.Builder.class, RETURNS_SELF);
        HttpClient http = mock(HttpClient.class);
        when(delegate.newBuilder()).thenReturn(builder);
        when(builder.build()).thenReturn(http);
        Config config = Config.empty();
        config.setMasterUrl("https://cluster.example.test");
        config.setOauthToken("fixture-token");
        config.setConnectionTimeout(1234);
        var factory = new RedirectRejectingHttpClientFactory(delegate);
        assertThat(factory.newBuilder(config).build()).isSameAs(http);
        verify(delegate).newBuilder();
        verify(delegate, never()).newBuilder(any(Config.class));
        verify(builder, never()).followAllRedirects();
        verify(builder).tag(config.getRequestConfig());
        verify(builder).connectTimeout(1234, TimeUnit.MILLISECONDS);
        verify(builder).sslContext(any(), any());
        verify(builder).addOrReplaceInterceptor(eq("TOKEN"), any(Interceptor.class));
    }

    @Test void wrapperForwardsEveryNonRedirectBuilderOptionWithoutClosingAnything() {
        HttpClient.Factory delegate = mock(HttpClient.Factory.class);
        HttpClient.Builder builder = mock(HttpClient.Builder.class, RETURNS_SELF);
        when(delegate.newBuilder()).thenReturn(builder);
        var wrapped = new RedirectRejectingHttpClientFactory(delegate).newBuilder();
        var address = new InetSocketAddress("127.0.0.1", 8888);
        var keys = new KeyManager[0];
        var trusts = new TrustManager[0];
        var interceptor = new Interceptor() { };
        Object tag = new Object();
        wrapped.connectTimeout(42, TimeUnit.MILLISECONDS).authenticatorNone()
                .sslContext(keys, trusts).proxyAddress(address).proxyAuthorization("fixture-proxy")
                .tlsVersions(TlsVersion.TLS_1_3).tlsServerName("cluster.example.test").preferHttp11()
                .proxyType(HttpClient.ProxyType.DIRECT).addOrReplaceInterceptor("safe", interceptor)
                .followAllRedirects().tag(tag);
        verify(builder).connectTimeout(42, TimeUnit.MILLISECONDS);
        verify(builder).authenticatorNone();
        verify(builder).sslContext(keys, trusts);
        verify(builder).proxyAddress(address);
        verify(builder).proxyAuthorization("fixture-proxy");
        verify(builder).tlsVersions(TlsVersion.TLS_1_3);
        verify(builder).tlsServerName("cluster.example.test");
        verify(builder).preferHttp11();
        verify(builder).proxyType(HttpClient.ProxyType.DIRECT);
        verify(builder).addOrReplaceInterceptor("safe", interceptor);
        verify(builder).tag(tag);
        verify(builder, never()).followAllRedirects();
    }
}
