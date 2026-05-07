package ly.payhub;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Discriminated NextAction returned in {@link Payment#nextAction}.
 *
 * Sealed interface (Java 17+) so callers can pattern-match exhaustively
 * via Java 21 switch:
 *
 * <pre>
 * switch (p.nextAction) {
 *   case NextAction.OtpRequired o -> ...;
 *   case NextAction.Redirect r    -> ...;
 *   case NextAction.QR q          -> ...;
 *   case NextAction.Lightbox l    -> ...;
 *   case null -> ...;
 * }
 * </pre>
 */
public sealed interface NextAction permits NextAction.OtpRequired, NextAction.Redirect, NextAction.QR, NextAction.Lightbox {

    record OtpRequired(String pspRef, String maskedDestination, String expiresAt) implements NextAction {}
    record Redirect(String url, String method, Map<String, String> fields, String expiresAt) implements NextAction {}
    record QR(String reference, String qrPayload, String expiresAt) implements NextAction {}
    record Lightbox(Map<String, String> params, String scriptUrl) implements NextAction {}

    static NextAction fromJson(JsonNode n) {
        String type = n.path("type").asText();
        return switch (type) {
            case "otp_required" -> new OtpRequired(
                    n.path("psp_ref").asText(""),
                    n.path("masked_destination").asText(""),
                    n.path("expires_at").asText(null));
            case "redirect" -> new Redirect(
                    n.path("url").asText(""),
                    n.path("method").asText("GET").toUpperCase(),
                    asStringMap(n.get("fields")),
                    n.path("expires_at").asText(null));
            case "qr" -> new QR(
                    n.path("reference").asText(""),
                    n.path("qr_payload").asText(""),
                    n.path("expires_at").asText(null));
            case "lightbox" -> {
                Map<String, String> params = asStringMap(n.get("params"));
                yield new Lightbox(params, params.get("lightbox_js_url"));
            }
            default -> throw new IllegalArgumentException("unknown next_action.type: " + type);
        };
    }

    private static Map<String, String> asStringMap(JsonNode n) {
        Map<String, String> out = new HashMap<>();
        if (n == null || !n.isObject()) return out;
        Iterator<Map.Entry<String, JsonNode>> it = n.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), e.getValue().asText());
        }
        return out;
    }
}
