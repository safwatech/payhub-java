package ly.payhub;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

final class PaymentDeserializer extends JsonDeserializer<Payment> {

    @Override
    public Payment deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode n = p.getCodec().readTree(p);
        String id = textOrNull(n, "id");
        String status = textOrNull(n, "status");
        String psp = textOrNull(n, "psp");
        String pspRef = textOrNull(n, "psp_ref");
        long amountMinor = n.path("amount_minor").asLong(0L);
        String currency = textOrNull(n, "currency");
        String merchantOrderRef = textOrNull(n, "merchant_order_ref");
        String hostedCheckoutUrl = textOrNull(n, "hosted_checkout_url");

        NextAction na = null;
        JsonNode naNode = n.get("next_action");
        if (naNode != null && !naNode.isNull()) {
            na = NextAction.fromJson(naNode);
        }
        return new Payment(id, status, psp, pspRef, na, amountMinor, currency, merchantOrderRef, hostedCheckoutUrl);
    }

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode v = parent.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
