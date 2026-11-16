package tn.digitworks.naanayamthavanai.loan.schedule;

/** Invalid loan terms. {@code code} maps to the API Error.code (HTTP 422). */
public class ScheduleException extends IllegalArgumentException {

    private final String code;

    public ScheduleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
