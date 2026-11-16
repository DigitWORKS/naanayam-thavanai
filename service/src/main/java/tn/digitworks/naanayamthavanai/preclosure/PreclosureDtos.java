package tn.digitworks.naanayamthavanai.preclosure;

import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanResponse;
import tn.digitworks.naanayamthavanai.payment.Payment;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.PaymentResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class PreclosureDtos {

    private PreclosureDtos() {
    }

    public record QuoteResponse(Long loanId, LocalDate asOf, BigDecimal outstandingPrincipal,
                                BigDecimal outstandingInterest, BigDecimal pendingPenalty, BigDecimal interestRebate,
                                BigDecimal preclosureCharge, BigDecimal settlementAmount) {
        static QuoteResponse of(long loanId, PreclosureCalculator.Quote q) {
            return new QuoteResponse(loanId, q.asOf(), q.outstandingPrincipal(), q.outstandingInterest(),
                    q.pendingPenalty(), q.interestRebate(), q.preclosureCharge(), q.settlementAmount());
        }
    }

    public record PreclosureConfirm(
            @NotBlank @Pattern(regexp = PaymentDtos.UUID, message = "must be a UUID") String clientTxnId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal settlementAmount,
            @NotNull Payment.Mode mode,
            @Size(max = 50) String upiRefNo,
            @NotNull OffsetDateTime paidOn) {
    }

    public record PreclosureResult(LoanResponse loan, PaymentResponse payment, QuoteResponse quote) {
    }
}
