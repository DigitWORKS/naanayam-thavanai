package tn.digitworks.naanayamthavanai.common;

import java.util.Map;

/** OpenAPI Error schema. */
public record ErrorResponse(String code, String message, Map<String, Object> details) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Map.of());
    }
}
