package ly.payhub;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RefundRequest {
    public final Long amountMinor;
    public final String reason;

    public RefundRequest(Long amountMinor, String reason) {
        this.amountMinor = amountMinor;
        this.reason = reason;
    }

    public static RefundRequest full() { return new RefundRequest(null, null); }
    public static RefundRequest partial(long amountMinor) { return new RefundRequest(amountMinor, null); }
    public static RefundRequest withReason(Long amountMinor, String reason) {
        return new RefundRequest(amountMinor, reason);
    }
}
