package ly.payhub;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

/**
 * Typed Payment row. {@code nextAction} is decoded into the sealed
 * {@link NextAction} hierarchy so callers can pattern-match exhaustively
 * (Java 21 sealed-interface switch) or use {@code instanceof}.
 */
@JsonDeserialize(using = PaymentDeserializer.class)
public final class Payment {
    public final String id;
    public final String status;
    public final String psp;
    public final String pspRef;
    public final NextAction nextAction;
    public final long amountMinor;
    public final String currency;
    public final String merchantOrderRef;
    public final String hostedCheckoutUrl;

    public Payment(String id, String status, String psp, String pspRef, NextAction nextAction,
                   long amountMinor, String currency, String merchantOrderRef, String hostedCheckoutUrl) {
        this.id = id;
        this.status = status;
        this.psp = psp;
        this.pspRef = pspRef;
        this.nextAction = nextAction;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.merchantOrderRef = merchantOrderRef;
        this.hostedCheckoutUrl = hostedCheckoutUrl;
    }
}
