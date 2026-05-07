package ly.payhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Webhook signature verification.
 *
 * Algorithmic reference: {@code app/core/signing.py}. Header is
 * {@code Hub-Signature: t=<unix>,v1=<hmac_sha256_hex>}; signed bytes are
 * {@code "{t}.".getBytes() + body}. Default tolerance ±300s.
 */
public final class WebhookEvent {

    public static final int DEFAULT_TOLERANCE_SECONDS = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final class Payload {
        public final String id;
        public final String type;
        public final String paymentId;
        public final String prevStatus;
        public final String newStatus;
        public final String source;
        public final Map<String, Object> payload;
        public final String createdAt;

        Payload(String id, String type, String paymentId, String prevStatus, String newStatus,
                String source, Map<String, Object> payload, String createdAt) {
            this.id = id;
            this.type = type;
            this.paymentId = paymentId;
            this.prevStatus = prevStatus;
            this.newStatus = newStatus;
            this.source = source;
            this.payload = payload;
            this.createdAt = createdAt;
        }
    }

    public static Payload verify(byte[] secret, byte[] body, String header) {
        return verify(secret, body, header, DEFAULT_TOLERANCE_SECONDS, Instant.now().getEpochSecond());
    }

    public static Payload verify(byte[] secret, byte[] body, String header, int toleranceSeconds, long now) {
        long t;
        String v1;
        Map<String, String> parts = parseParts(header);
        if (!parts.containsKey("t") || !parts.containsKey("v1")) {
            throw new MalformedHeader("Hub-Signature missing t or v1: " + header);
        }
        try {
            t = Long.parseLong(parts.get("t"));
        } catch (NumberFormatException e) {
            throw new MalformedHeader("Hub-Signature t is not an integer: " + parts.get("t"));
        }
        v1 = parts.get("v1");

        long skew = Math.abs(now - t);
        if (skew > toleranceSeconds) {
            throw new TimestampOutOfTolerance((int) skew);
        }

        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update((Long.toString(t) + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            expected = hexLower(mac.doFinal()).getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new RuntimeException("payhub: HMAC unavailable: " + e.getMessage(), e);
        }
        byte[] received = v1.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new InvalidSignature("Hub-Signature v1 does not match");
        }

        if (body.length == 0) {
            throw new InvalidSignature("webhook body is empty / not JSON");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (IOException e) {
            throw new InvalidSignature("webhook body is not JSON: " + e.getMessage());
        }
        if (!root.isObject()) {
            throw new InvalidSignature("webhook body is not a JSON object");
        }
        Map<String, Object> payload = new HashMap<>();
        JsonNode pl = root.get("payload");
        if (pl != null && pl.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = pl.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                payload.put(e.getKey(), MAPPER.convertValue(e.getValue(), Object.class));
            }
        }
        return new Payload(
                root.path("id").asText(""),
                root.path("type").asText(""),
                root.path("payment_id").asText(""),
                root.path("prev_status").isNull() ? null : root.path("prev_status").asText(null),
                root.path("new_status").asText(""),
                root.path("source").asText(""),
                payload,
                root.path("created_at").asText(""));
    }

    private static Map<String, String> parseParts(String header) {
        Map<String, String> out = new HashMap<>();
        for (String seg : header.split(",")) {
            int eq = seg.indexOf('=');
            if (eq <= 0) continue;
            out.put(seg.substring(0, eq).trim(), seg.substring(eq + 1).trim());
        }
        return out;
    }

    private static String hexLower(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public abstract static class WebhookSignatureError extends RuntimeException {
        WebhookSignatureError(String m) { super(m); }
    }

    public static final class MalformedHeader extends WebhookSignatureError {
        public MalformedHeader(String m) { super(m); }
    }

    public static final class TimestampOutOfTolerance extends WebhookSignatureError {
        public final int skewSeconds;
        public TimestampOutOfTolerance(int skew) {
            super("payhub: webhook timestamp out of tolerance: " + skew + "s skew");
            this.skewSeconds = skew;
        }
    }

    public static final class InvalidSignature extends WebhookSignatureError {
        public InvalidSignature(String m) { super(m); }
    }
}
