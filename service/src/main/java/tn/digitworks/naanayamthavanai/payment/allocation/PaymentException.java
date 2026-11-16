package tn.digitworks.naanayamthavanai.payment.allocation;

/** Invalid payment. {@code code} maps to the API Error.code (HTTP 422). */
public class PaymentException extends IllegalArgumentException {

    private final String code;

    public PaymentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
