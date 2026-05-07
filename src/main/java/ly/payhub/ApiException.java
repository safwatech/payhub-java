package ly.payhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown for any non-2xx HTTP response with a parseable error envelope.
 * Subclasses encode the broad failure class; the dot-path {@link #code}
 * carries the precise server-side identifier.
 */
public class ApiException extends RuntimeException {
    public final String code;
    public final int httpStatus;
    public final Map<String, Object> details;
    public final String requestId;

    ApiException(String message, String code, int httpStatus, Map<String, Object> details, String requestId) {
        super(message + (requestId != null ? " [request_id=" + requestId + "]" : ""));
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details == null ? Collections.emptyMap() : details;
        this.requestId = requestId;
    }

    public static class Authentication extends ApiException {
        public Authentication(String m, String c, int s, Map<String, Object> d, String r) { super(m, c, s, d, r); }
    }
    public static class Permission extends ApiException {
        public Permission(String m, String c, int s, Map<String, Object> d, String r) { super(m, c, s, d, r); }
    }
    public static class NotFound extends ApiException {
        public NotFound(String m, String c, int s, Map<String, Object> d, String r) { super(m, c, s, d, r); }
    }
    public static class Validation extends ApiException {
        public Validation(String m, String c, int s, Map<String, Object> d, String r) { super(m, c, s, d, r); }
    }
    public static class IdempotencyConflict extends ApiException {
        public IdempotencyConflict(String m, String c, int s, Map<String, Object> d, String r) { super(m, c, s, d, r); }
    }
    public static class RateLimited extends ApiException {
        public final Integer retryAfter;
        public RateLimited(String m, String c, int s, Map<String, Object> d, String r, Integer retryAfter) {
            super(m, c, s, d, r);
            this.retryAfter = retryAfter;
        }
    }
    public static class Gateway extends ApiException {
        public Gateway(String m, String c, int s, Map<String, Object> d, String r) { super(m, c, s, d, r); }
    }
    public static class Server extends ApiException {
        public Server(String m, String c, int s, Map<String, Object> d, String r) { super(m, c, s, d, r); }
    }

    static ApiException fromEnvelope(int status, byte[] body, String retryAfterHeader, ObjectMapper mapper) {
        String code = "hub.unknown";
        String message = "HTTP " + status;
        Map<String, Object> details = Collections.emptyMap();
        String requestId = null;
        if (body != null && body.length > 0) {
            try {
                JsonNode root = mapper.readTree(body);
                JsonNode err = root.path("error");
                if (err.isObject()) {
                    code = err.path("code").asText(code);
                    message = err.path("message").asText(message);
                    requestId = err.path("request_id").isNull() ? null : err.path("request_id").asText(null);
                    JsonNode dn = err.path("details");
                    if (dn.isObject()) {
                        details = new LinkedHashMap<>();
                        Iterator<Map.Entry<String, JsonNode>> it = dn.fields();
                        while (it.hasNext()) {
                            Map.Entry<String, JsonNode> e = it.next();
                            details.put(e.getKey(), mapper.convertValue(e.getValue(), Object.class));
                        }
                    }
                }
            } catch (Exception ignore) { /* fall through with defaults */ }
        }
        Integer retryAfter = null;
        if (retryAfterHeader != null) {
            try { retryAfter = Integer.parseInt(retryAfterHeader); } catch (NumberFormatException ignored) {}
        }
        return switch (status) {
            case 401 -> new Authentication(message, code, status, details, requestId);
            case 403 -> new Permission(message, code, status, details, requestId);
            case 404 -> new NotFound(message, code, status, details, requestId);
            case 409 -> new IdempotencyConflict(message, code, status, details, requestId);
            case 422 -> new Validation(message, code, status, details, requestId);
            case 429 -> new RateLimited(message, code, status, details, requestId, retryAfter);
            default -> {
                if (status >= 500 && status < 600) {
                    yield code.startsWith("gateway.")
                            ? new Gateway(message, code, status, details, requestId)
                            : new Server(message, code, status, details, requestId);
                }
                yield new ApiException(message, code, status, details, requestId);
            }
        };
    }
}
