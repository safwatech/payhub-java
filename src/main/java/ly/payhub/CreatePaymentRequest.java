package ly.payhub;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * POST /v1/payments body. Field names map to the server's snake_case
 * via Jackson's {@code SnakeCaseStrategy} configured on the SDK's
 * shared {@link com.fasterxml.jackson.databind.ObjectMapper}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class CreatePaymentRequest {
    public final String psp;
    public final String merchantOrderRef;
    public final long amountMinor;
    public final String currency;
    public final Map<String, Object> customer;
    public final Map<String, Object> returnUrls;
    public final Map<String, Object> metadata;
    public final Boolean hostedCheckout;

    private CreatePaymentRequest(Builder b) {
        this.psp = b.psp;
        this.merchantOrderRef = b.merchantOrderRef;
        this.amountMinor = b.amountMinor;
        this.currency = b.currency;
        this.customer = b.customer;
        this.returnUrls = b.returnUrls;
        this.metadata = b.metadata;
        this.hostedCheckout = b.hostedCheckout;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String psp;
        private String merchantOrderRef;
        private long amountMinor;
        private String currency;
        private Map<String, Object> customer;
        private Map<String, Object> returnUrls;
        private Map<String, Object> metadata;
        private Boolean hostedCheckout;

        public Builder psp(String v) { this.psp = v; return this; }
        public Builder merchantOrderRef(String v) { this.merchantOrderRef = v; return this; }
        public Builder amountMinor(long v) { this.amountMinor = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder customer(Map<String, Object> v) { this.customer = v; return this; }
        public Builder returnUrls(Map<String, Object> v) { this.returnUrls = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }
        public Builder hostedCheckout(boolean v) { this.hostedCheckout = v; return this; }
        public CreatePaymentRequest build() { return new CreatePaymentRequest(this); }
    }
}
