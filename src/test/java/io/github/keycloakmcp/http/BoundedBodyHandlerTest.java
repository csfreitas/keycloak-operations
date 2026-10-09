package io.github.keycloakmcp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BoundedBodyHandlerTest {
    private HttpResponse.BodySubscriber<byte[]> subscriber(int limit, Duration timeout, String length) {
        var info = mock(HttpResponse.ResponseInfo.class);
        when(info.headers()).thenReturn(HttpHeaders.of(length == null ? Map.of() : Map.of("Content-Length", List.of(length)), (k,v) -> true));
        return new BoundedBodyHandler(limit, timeout).apply(info);
    }

    @Test void acceptsExactlyTheLimitAcrossChunks() {
        var body = subscriber(4, Duration.ofSeconds(2), null);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2})));
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{3,4})));
        body.onComplete();
        assertThat(body.getBody().toCompletableFuture().join()).containsExactly(1,2,3,4);
        verify(subscription, never()).cancel();
    }

    @Test void rejectsChunkedOverflowBeforeRetainingMoreBytes() {
        var body = subscriber(4, Duration.ofSeconds(2), null);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2,3})));
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{4,5})));
        var failure = body.getBody().handle((value,error) -> error).toCompletableFuture().join();
        assertThat(BoundedBodyHandler.isLimitFailure(failure)).isTrue();
        verify(subscription).cancel();
        body.onNext(List.of(ByteBuffer.wrap(new byte[20])));
        body.onComplete();
        assertThat(body.getBody().toCompletableFuture().isCompletedExceptionally()).isTrue();
    }

    @Test void rejectsDeclaredOversizeAndCancelsSubscriptionOnArrival() {
        var body = subscriber(4, Duration.ofSeconds(2), "5");
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        assertThat(BoundedBodyHandler.isLimitFailure(body.getBody().handle((value,error) -> error).toCompletableFuture().join())).isTrue();
        verify(subscription).cancel();
        verify(subscription, never()).request(anyLong());
    }

    @Test void stalledBodyTimesOutAndCancelsItsSubscription() throws Exception {
        var body = subscriber(4, Duration.ofMillis(100), null);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        Throwable error = body.getBody().handle((value,failure) -> failure).toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertThat(BoundedBodyHandler.isTimeoutFailure(error)).isTrue();
        verify(subscription, timeout(1000)).cancel();
    }

    @Test void upstreamErrorCancelsAndCannotBecomeASuccess() {
        var body = subscriber(4, Duration.ofSeconds(2), null);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onError(new java.io.IOException("upstream-failure"));
        body.onComplete();
        assertThat(body.getBody().toCompletableFuture().isCompletedExceptionally()).isTrue();
        verify(subscription).cancel();
    }

    @Test void duplicateSubscriptionIsRejected() {
        var body = subscriber(4, Duration.ofSeconds(2), null);
        var original = mock(Flow.Subscription.class);
        var duplicate = mock(Flow.Subscription.class);
        body.onSubscribe(original); body.onSubscribe(duplicate); body.onComplete();
        verify(duplicate).cancel(); verify(original, never()).cancel();
    }

    @Test void invalidBoundsAreRejected() {
        assertThatThrownBy(() -> new BoundedBodyHandler(0, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundedBodyHandler(4, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundedBodyHandler(4, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void remainingBudgetIsEvaluatedAtHeadersNotHandlerConstruction() throws Exception {
        var left = new AtomicReference<>(Duration.ofSeconds(2));
        var handler = new BoundedBodyHandler(4, Duration.ofSeconds(2), left::get);
        left.set(Duration.ofMillis(25));
        var info = mock(HttpResponse.ResponseInfo.class);
        when(info.headers()).thenReturn(HttpHeaders.of(Map.of(), (k,v) -> true));
        var body = handler.apply(info);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        Throwable failure = body.getBody().handle((value,error) -> error).toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(BoundedBodyHandler.isTimeoutFailure(failure)).isTrue();
        verify(subscription, timeout(1000)).cancel();
    }

    @Test void exhaustedBudgetCancelsBeforeRequestingAnyBodyBytes() {
        var info = mock(HttpResponse.ResponseInfo.class);
        when(info.headers()).thenReturn(HttpHeaders.of(Map.of(), (k,v) -> true));
        var body = new BoundedBodyHandler(4, Duration.ofSeconds(2), () -> Duration.ZERO).apply(info);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1})));
        body.onComplete();
        Throwable failure = body.getBody().handle((value,error) -> error).toCompletableFuture().join();
        assertThat(BoundedBodyHandler.isTimeoutFailure(failure)).isTrue();
        verify(subscription).cancel();
        verify(subscription, never()).request(anyLong());
    }

    @Test void operationBudgetCannotExtendConfiguredBodyTimeout() throws Exception {
        var info = mock(HttpResponse.ResponseInfo.class);
        when(info.headers()).thenReturn(HttpHeaders.of(Map.of(), (k,v) -> true));
        var body = new BoundedBodyHandler(4, Duration.ofMillis(25), () -> Duration.ofSeconds(2)).apply(info);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        Throwable failure = body.getBody().handle((value,error) -> error).toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(BoundedBodyHandler.isTimeoutFailure(failure)).isTrue();
        verify(subscription, timeout(1000)).cancel();
    }
}
