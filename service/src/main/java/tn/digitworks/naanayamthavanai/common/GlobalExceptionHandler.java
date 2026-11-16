package tn.digitworks.naanayamthavanai.common;

import tn.digitworks.naanayamthavanai.loan.schedule.ScheduleException;
import tn.digitworks.naanayamthavanai.payment.allocation.PaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage(), e.getDetails()));
    }

    @ExceptionHandler(ScheduleException.class)
    ResponseEntity<ErrorResponse> schedule(ScheduleException e) {
        return unprocessable(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(PaymentException.class)
    ResponseEntity<ErrorResponse> payment(PaymentException e) {
        return unprocessable(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(f.getField(), f.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", "Invalid request", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("BAD_REQUEST", "Malformed request"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of("FILE_TOO_LARGE", "File is larger than the allowed size"));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ErrorResponse> denied(Exception e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", "You do not have access to this action"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DUPLICATE", "A record with the same unique value already exists"));
    }

    private static ResponseEntity<ErrorResponse> unprocessable(String code, String message) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ErrorResponse.of(code, message));
    }
}
