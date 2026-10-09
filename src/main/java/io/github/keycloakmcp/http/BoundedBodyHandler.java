package io.github.keycloakmcp.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Bounds received bytes before JSON parsing, including chunked and stalled bodies. */
public final class BoundedBodyHandler implements HttpResponse.BodyHandler<byte[]> {
    private final int maxBytes;
    private final Duration bodyTimeout;
    private final Supplier<Duration> remaining;

    public BoundedBodyHandler(int maxBytes, Duration bodyTimeout) {
        this(maxBytes, bodyTimeout, null);
    }

    /** Recomputes a shared operation's remaining time after response headers arrive. */
    public BoundedBodyHandler(int maxBytes, Duration bodyTimeout, Supplier<Duration> remaining) {
        if (maxBytes < 1 || bodyTimeout == null || bodyTimeout.isNegative() || bodyTimeout.isZero()) {
            throw new IllegalArgumentException("Positive response byte and duration limits are required");
        }
        this.maxBytes = maxBytes;
        this.bodyTimeout = bodyTimeout;
        this.remaining = remaining;
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo info) {
        Duration timeout = bodyTimeout;
        if (remaining != null) {
            Duration left = remaining.get();
            if (left == null || left.isNegative()) left = Duration.ZERO;
            if (left.compareTo(timeout) < 0) timeout = left;
        }
        var subscriber = new LimitedSubscriber(maxBytes, timeout);
        if (info.headers().firstValueAsLong("Content-Length").orElse(0) > maxBytes) {
            subscriber.onError(new ResponseLimitException());
        }
        return subscriber;
    }

    public static boolean isLimitFailure(Throwable failure) {
        return hasCause(failure, ResponseLimitException.class);
    }

    public static boolean isTimeoutFailure(Throwable failure) {
        return hasCause(failure, TimeoutException.class)
                || hasCause(failure, java.net.http.HttpTimeoutException.class);
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            if (type.isInstance(failure)) return true;
        }
        return false;
    }

    private static final class ResponseLimitException extends IOException {
        ResponseLimitException() { super("Response byte limit exceeded"); }
    }

    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        LimitedSubscriber(int maxBytes, Duration timeout) {
            this.maxBytes = maxBytes;
            if (timeout.isZero()) body.completeExceptionally(new TimeoutException("Response body deadline exceeded"));
            else body.orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
            body.whenComplete((result, failure) -> {
                synchronized (this) {
                    if (failure != null && subscription != null) subscription.cancel();
                    bytes = null;
                }
            });
        }

        @Override
        public CompletionStage<byte[]> getBody() { return body; }

        @Override
        public void onSubscribe(Flow.Subscription incoming) {
            synchronized (this) {
                if (subscription != null || body.isDone()) {
                    incoming.cancel();
                    return;
                }
                subscription = incoming;
            }
            incoming.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            Flow.Subscription next;
            synchronized (this) {
                if (body.isDone()) return;
                long additional = 0;
                for (ByteBuffer buffer : buffers) additional += buffer.remaining();
                if (additional > maxBytes - bytes.size()) {
                    onError(new ResponseLimitException());
                    return;
                }
                for (ByteBuffer buffer : buffers) {
                    byte[] chunk = new byte[buffer.remaining()];
                    buffer.get(chunk);
                    bytes.writeBytes(chunk);
                }
                next = subscription;
            }
            if (next != null) next.request(1);
        }

        @Override
        public synchronized void onError(Throwable failure) { body.completeExceptionally(failure); }

        @Override
        public synchronized void onComplete() {
            if (!body.isDone()) body.complete(bytes.toByteArray());
        }
    }
}
