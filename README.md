# PayHub Java SDK

Official PayHub SDK for the JVM. Java 17+, Jackson-only runtime
dependency, sealed `NextAction`, typed `ApiException` hierarchy, and a
webhook verifier that throws on every failure mode so callers can't
forget the unhappy path.

## Install

Maven:

```xml
<dependency>
  <groupId>ly.payhub</groupId>
  <artifactId>payhub</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle:

```kotlin
dependencies {
    implementation("ly.payhub:payhub:1.0.0")
}
```

On Maven Central; sources + javadoc + GPG signatures attached.

> **PayHub API:** v1 · **JDK:** ≥ 17 · **License:** MIT

## Quickstart — Sadad OTP

```java
import ly.payhub.*;

var client = Payhub.builder(System.getenv("PAYHUB_API_KEY"))
    .baseUrl("https://app.payhub.ly")
    .build();

var payment = client.payments().create(new CreatePaymentRequest(
    "sadad",
    "ord-42",
    4500,
    Map.of("msisdn", "218910000001", "birth_year", 1990)
));

switch (payment.nextAction()) {
    case NextAction.OtpRequired otp -> System.out.println("OTP sent to " + otp.maskedDestination());
    case NextAction.Redirect r      -> redirect(r.url());
    case NextAction.Qr q            -> showQR(q.qrPayload());
    case NextAction.Lightbox l      -> mountLightbox(l.params());
    case null                       -> {} // already final
}

var settled = client.payments().confirmOtp(payment.id(), "123456");
```

The SDK auto-mints a UUID4 `Idempotency-Key` for `create` / `confirmOtp`
/ `refund`. Pass your own as the trailing argument to make retries safe
across process restarts.

## Webhook receiver (Spring Boot)

> ⚠️ **Bind to `byte[]`, not a parsed DTO.** Re-serializing JSON changes
> whitespace and breaks the HMAC. Spring's `@RequestBody byte[]` gives
> you the raw bytes.

```java
import ly.payhub.WebhookEvent;

@PostMapping("/webhooks/payhub")
public ResponseEntity<Void> receive(
        @RequestBody byte[] body,
        @RequestHeader("Hub-Signature") String sig) {
    try {
        var ev = WebhookEvent.verify(
            System.getenv("PAYHUB_WEBHOOK_SECRET").getBytes(),
            body,
            sig
        );
        switch (ev.type) {
            case "payment.succeeded" -> /* mark order paid */ {}
            case "payment.failed", "payment.expired" -> /* unlock cart */ {}
            case "payment.refunded" -> /* update accounting */ {}
            default -> {} // forward-compatible
        }
        return ResponseEntity.ok().build();
    } catch (WebhookEvent.WebhookSignatureError e) {
        return ResponseEntity.status(401).build();
    }
}
```

Default replay tolerance is 300 s. Override via the 5-arg
`WebhookEvent.verify(secret, body, header, toleranceSeconds, now)`.

## Errors

| Class | Fires on |
| --- | --- |
| `ApiException.Authentication` | 401 — bad API key |
| `ApiException.Permission` | 403 |
| `ApiException.NotFound` | 404 |
| `ApiException.Validation` | 422 |
| `ApiException.IdempotencyConflict` | 409 — same key, different body |
| `ApiException.RateLimited` | 429 — exposes `retryAfter` |
| `ApiException.Gateway` | 5xx + `gateway.<psp>.*` code |
| `ApiException.Server` | other 5xx |
| `TransportException.Timeout`, `.Connection`, `.Decode` | network / serialization |
| `WebhookEvent.MalformedHeader` | header missing `t=` or `v1=` |
| `WebhookEvent.TimestampOutOfTolerance` | clock skew > tolerance (carries `skewSeconds`) |
| `WebhookEvent.InvalidSignature` | HMAC mismatch / non-JSON body |

Every `ApiException` carries `.code`, `.httpStatus`, `.details`,
`.requestId`. Log `requestId` to support tickets — it matches the
server-side `X-Request-ID`.

## Configuration

```java
Payhub.builder("phk_…")
    .baseUrl("https://app.payhub.ly")
    .timeout(Duration.ofSeconds(30))
    .maxRetries(2)                       // idempotent calls only
    .httpClient(myJdkHttpClient)         // injection seam (proxies, tests)
    .userAgentSuffix("Acme/1.2")
    .build();
```

## Versioning

Independent semver. Compatible with PayHub API v1.

## Development

```
mvn -B -ntp test                                      # full suite
mvn -B -ntp test -Dtest=WebhookSigningVectorsTest     # signing vectors only
```

The webhook vector test loads
`../shared/test-vectors/webhook-signing.json` — the canonical spec
consumed by every PayHub SDK and by the server's own
`tests/unit/test_webhook_signing_vectors.py`.

## License

MIT — see `LICENSE`.
