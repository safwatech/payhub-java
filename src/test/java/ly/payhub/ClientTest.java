package ly.payhub;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private String baseUrl() { return "http://127.0.0.1:" + port; }

    private static final String PAYMENT_BODY = """
            {"id":"pay_1","status":"requires_action","psp":"sadad","psp_ref":"TXN_1",
             "next_action":{"type":"otp_required","psp_ref":"TXN_1","masked_destination":"2189...12"},
             "amount_minor":4500,"currency":"LYD","merchant_order_ref":"ord-1"}
            """;

    @Test
    void createDecodesPaymentAndSetsHeaders() throws IOException {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> idem = new AtomicReference<>();
        server.createContext("/v1/payments", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            idem.set(ex.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] body = PAYMENT_BODY.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(201, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        Payhub c = Payhub.builder("phk_a.b").baseUrl(baseUrl()).maxRetries(0).build();
        Payment p = c.payments().create(CreatePaymentRequest.builder()
                .psp("sadad").merchantOrderRef("ord-1").amountMinor(4500)
                .customer(Map.of("msisdn", "218910000001", "birth_year", 1990))
                .build());
        assertEquals("requires_action", p.status);
        assertNotNull(p.nextAction);
        assertInstanceOf(NextAction.OtpRequired.class, p.nextAction);
        assertEquals("Bearer phk_a.b", auth.get());
        assertNotNull(idem.get());
    }

    @Test
    void rejectsBadApiKey() {
        assertThrows(IllegalArgumentException.class, () -> Payhub.builder("not-a-key").build());
    }

    @Test
    void maps401ToAuthentication() throws IOException {
        server.createContext("/v1/health", ex -> {
            byte[] body = "{\"error\":{\"code\":\"hub.unauthenticated\",\"message\":\"no\"}}".getBytes();
            ex.sendResponseHeaders(401, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        Payhub c = Payhub.builder("phk_a.b").baseUrl(baseUrl()).maxRetries(0).build();
        assertThrows(ApiException.Authentication.class, () -> c.health().check());
    }

    @Test
    void retriesOn503ThenSucceeds() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/health", ex -> {
            int n = calls.incrementAndGet();
            byte[] body = (n == 1
                    ? "{\"error\":{\"code\":\"hub.unavailable\",\"message\":\"x\"}}"
                    : "{\"status\":\"ok\",\"psps\":[\"sadad\"]}").getBytes();
            ex.sendResponseHeaders(n == 1 ? 503 : 200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        Payhub c = Payhub.builder("phk_a.b").baseUrl(baseUrl()).maxRetries(2).build();
        Health h = c.health().check();
        assertEquals("ok", h.status());
        assertEquals(2, calls.get());
    }
}
