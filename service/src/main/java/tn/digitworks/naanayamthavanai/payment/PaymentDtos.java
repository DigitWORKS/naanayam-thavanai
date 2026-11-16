package tn.digitworks.naanayamthavanai.payment;

import tn.digitworks.naanayamthavanai.common.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class PaymentDtos {

    public static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private PaymentDtos() {
    }

    public record PaymentCreate(
            @NotBlank @Pattern(regexp = UUID, message = "must be a UUID") String clientTxnId,
            @NotNull Long loanId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull Payment.Mode mode,
            @Size(max = 50) String upiRefNo,
            @NotNull OffsetDateTime paidOn,
            @Size(max = 255) String remarks) {
    }

    public record SyncRequest(@NotEmpty @Size(max = 500) List<@Valid PaymentCreate> payments) {
    }

    public record ReverseRequest(@NotBlank @Size(max = 200) String reason) {
    }

    public record AllocationResponse(Long installmentId, int seqNo, LocalDate dueDate, BigDecimal amount,
                                     BigDecimal penaltyPart) {
    }

    public record PaymentResponse(Long id, String clientTxnId, Long loanId, BigDecimal amount,
                                  Payment.EntryType entryType, Payment.Purpose purpose, Long reversalOf,
                                  Payment.Mode mode, String upiRefNo, OffsetDateTime paidOn, Long collectedBy,
                                  String collectedByName, List<AllocationResponse> allocations,
                                  BigDecimal balanceAfter) {
    }

    public record LedgerEntry(Long id, String clientTxnId, Long loanId, BigDecimal amount,
                              Payment.EntryType entryType, Payment.Purpose purpose, Long reversalOf,
                              Payment.Mode mode, String upiRefNo, OffsetDateTime paidOn, Long collectedBy,
                              String collectedByName, List<AllocationResponse> allocations,
                              BigDecimal balanceAfter, BigDecimal runningBalance) {
    }

    public enum SyncStatus { CREATED, DUPLICATE, FAILED }

    public record SyncResult(String clientTxnId, SyncStatus status, Long paymentId, ErrorResponse error) {
    }

    /** Service result: the payment plus whether it was newly created (201) or a replay (200). */
    public record Recorded(PaymentResponse payment, boolean created) {
    }
}
