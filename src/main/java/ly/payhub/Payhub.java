package ly.payhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Official PayHub SDK for Java. Construct via {@link Builder}; share a
 * single instance across threads — the underlying {@link HttpClient}
 * is thread-safe.
 *
 * <pre>
 * Payhub client = Payhub.builder("phk_…").build();
 * Payment p = client.payments().create(req);
 * </pre>
 */
public final class Payhub {

    public static final String VERSION = "1.0.0";

    private static final String DEFAULT_BASE_URL = "https://app.payhub.ly";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_RETRIES = 2;

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;
    private final Duration timeout;
    private final int maxRetries;
    private final ObjectMapper mapper;
    private final String userAgent;
    private final SecureRandom random = new SecureRandom();

    private final Payments payments;
    private final HealthResource health;

    private Payhub(Builder b) {
        if (b.apiKey == null || !b.apiKey.startsWith("phk_")) {
            throw new IllegalArgumentException("PayHub API key must start with \"phk_\"");
        }
        this.apiKey = b.apiKey;
        this.baseUrl = b.baseUrl.replaceAll("/+$", "");
        this.timeout = b.timeout;
        this.maxRetries = b.maxRetries;
        this.http = b.httpClient != null
                ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(b.timeout).build();
        this.mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String base = "payhub-java/" + VERSION + " (jdk " + System.getProperty("java.version") + ")";
        this.userAgent = b.userAgentSuffix == null ? base : base + " " + b.userAgentSuffix;
        this.payments = new Payments(this);
        this.health = new HealthResource(this);
    }

    public static Builder builder(String apiKey) {
        return new Builder().apiKey(apiKey);
    }

    public Payments payments() { return payments; }
    public HealthResource health() { return health; }
    public ObjectMapper mapper() { return mapper; }

    public static final class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_RETRIES;
        private HttpClient httpClient;
        private String userAgentSuffix;

        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder timeout(Duration v) { this.timeout = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder httpClient(HttpClient v) { this.httpClient = v; return this; }
        public Builder userAgentSuffix(String v) { this.userAgentSuffix = v; return this; }
        public Payhub build() { return new Payhub(this); }
    }

    // package-private — used by resource classes.
    <T> T request(String method, String path, Object body, String idempotencyKey, boolean retriable, Class<T> dst) {
        byte[] bytes;
        try {
            bytes = body == null ? null : mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new TransportException.Decode("encode failed: " + e.getMessage());
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent);
        if (bytes != null) {
            rb.header("Content-Type", "application/json");
        }
        if (idempotencyKey != null) {
            rb.header("Idempotency-Key", idempotencyKey);
        }
        HttpRequest.BodyPublisher pub = bytes == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(bytes);
        rb.method(method, pub);

        HttpRequest req = rb.build();
        int attempts = retriable ? Math.max(1, maxRetries + 1) : 1;
        Throwable lastErr = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            HttpResponse<byte[]> resp;
            try {
                resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            } catch (java.net.http.HttpTimeoutException e) {
                lastErr = new TransportException.Timeout(e.getMessage());
                if (retriable && attempt + 1 < attempts) { sleepBackoff(attempt); continue; }
                throw (TransportException) lastErr;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                lastErr = new TransportException.Connection(e.getMessage());
                if (retriable && attempt + 1 < attempts) { sleepBackoff(attempt); continue; }
                throw (TransportException) lastErr;
            }
            int status = resp.statusCode();
            byte[] respBody = resp.body();
            if (status >= 200 && status < 300) {
                if (dst == Void.class || respBody == null || respBody.length == 0) {
                    return null;
                }
                try {
                    return mapper.readValue(respBody, dst);
                } catch (IOException e) {
                    throw new TransportException.Decode("decode failed: " + e.getMessage());
                }
            }
            ApiException err = ApiException.fromEnvelope(status, respBody, resp.headers().firstValue("retry-after").orElse(null), mapper);
            if (retriable && (status >= 500 || status == 429) && attempt + 1 < attempts) {
                long wait = err instanceof ApiException.RateLimited rl && rl.retryAfter != null
                        ? rl.retryAfter * 1000L
                        : backoffMs(attempt);
                sleep(wait);
                lastErr = err;
                continue;
            }
            throw err;
        }
        if (lastErr instanceof RuntimeException re) throw re;
        throw new TransportException.Connection("payhub: unreachable retry loop");
    }

    private long backoffMs(int attempt) {
        long base = 500L * (1L << attempt);
        double jitter = 0.8 + random.nextDouble() * 0.4;
        return (long) (base * jitter);
    }

    private void sleepBackoff(int attempt) { sleep(backoffMs(attempt)); }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    String uuid4() { return UUID.randomUUID().toString(); }

    /** /v1/health resource. */
    public final class HealthResource {
        private final Payhub c;
        HealthResource(Payhub c) { this.c = c; }
        public Health check() {
            return c.request("GET", "/v1/health", null, null, true, Health.class);
        }
    }

    /** /v1/payments resource. */
    public final class Payments {
        private final Payhub c;
        Payments(Payhub c) { this.c = c; }

        public Payment create(CreatePaymentRequest req) { return create(req, null); }
        public Payment create(CreatePaymentRequest req, String idempotencyKey) {
            String key = idempotencyKey != null ? idempotencyKey : c.uuid4();
            return c.request("POST", "/v1/payments", req, key, true, Payment.class);
        }

        public Payment confirmOtp(String paymentId, String code) { return confirmOtp(paymentId, code, null); }
        public Payment confirmOtp(String paymentId, String code, String idempotencyKey) {
            String key = idempotencyKey != null ? idempotencyKey : c.uuid4();
            Map<String, String> body = Map.of("code", code);
            return c.request("POST", "/v1/payments/" + paymentId + "/otp", body, key, true, Payment.class);
        }

        public Payment refund(String paymentId, RefundRequest req) { return refund(paymentId, req, null); }
        public Payment refund(String paymentId, RefundRequest req, String idempotencyKey) {
            String key = idempotencyKey != null ? idempotencyKey : c.uuid4();
            return c.request("POST", "/v1/payments/" + paymentId + "/refund", req, key, true, Payment.class);
        }

        public Payment retrieve(String paymentId) {
            return c.request("GET", "/v1/payments/" + paymentId, null, null, true, Payment.class);
        }
    }
}
