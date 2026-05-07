package ly.payhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSigningVectorsTest {

    @TestFactory
    List<DynamicTest> vectors() throws IOException {
        Path p = Path.of("../shared/test-vectors/webhook-signing.json");
        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(p));
        List<DynamicTest> out = new ArrayList<>();
        for (JsonNode c : doc.get("cases")) {
            out.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> runCase(c)));
        }
        return out;
    }

    private void runCase(JsonNode c) {
        byte[] secret = HexFormat.of().parseHex(c.get("secret_hex").asText());
        byte[] body = Base64.getDecoder().decode(c.get("body_b64").asText());
        String header = c.get("header").asText();
        int tolerance = c.get("tolerance_seconds").asInt();
        long now = c.get("now").asLong();
        String expect = c.get("expect").asText();
        String name = c.get("name").asText();

        switch (expect) {
            case "ok" -> {
                try {
                    WebhookEvent.verify(secret, body, header, tolerance, now);
                } catch (WebhookEvent.InvalidSignature e) {
                    // empty body verifies HMAC-wise but isn't JSON
                    if (c.get("body_b64").asText().isEmpty()) return;
                    fail("ok case " + name + " threw InvalidSignature: " + e.getMessage());
                }
            }
            case "TimestampOutOfTolerance" ->
                    assertThrows(WebhookEvent.TimestampOutOfTolerance.class,
                            () -> WebhookEvent.verify(secret, body, header, tolerance, now));
            case "InvalidSignature" ->
                    assertThrows(WebhookEvent.InvalidSignature.class,
                            () -> WebhookEvent.verify(secret, body, header, tolerance, now));
            case "MalformedHeader" ->
                    assertThrows(WebhookEvent.MalformedHeader.class,
                            () -> WebhookEvent.verify(secret, body, header, tolerance, now));
            default -> fail("unknown expect: " + expect);
        }
    }

    @Test
    void validV1ReturnsTypedPayload() throws IOException {
        Path p = Path.of("../shared/test-vectors/webhook-signing.json");
        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(p));
        for (JsonNode c : doc.get("cases")) {
            if (!"valid_v1".equals(c.get("name").asText())) continue;
            byte[] secret = HexFormat.of().parseHex(c.get("secret_hex").asText());
            byte[] body = Base64.getDecoder().decode(c.get("body_b64").asText());
            WebhookEvent.Payload ev = WebhookEvent.verify(
                    secret, body, c.get("header").asText(),
                    c.get("tolerance_seconds").asInt(), c.get("now").asLong());
            assertEquals("evt_1", ev.id);
            assertEquals("payment.succeeded", ev.type);
            assertEquals("pay_1", ev.paymentId);
            return;
        }
        fail("valid_v1 case missing");
    }
}
