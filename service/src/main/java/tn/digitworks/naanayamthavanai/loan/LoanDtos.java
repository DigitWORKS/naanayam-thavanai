package tn.digitworks.naanayamthavanai.loan;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestCollection;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanSchedule;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class LoanDtos {

    private LoanDtos() {
    }

    public record LoanCreate(
            @NotNull Long clientId,
            @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal principal,
            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal interestRate,
            @NotNull InterestMethod interestMethod,
            @NotNull InterestCollection interestCollection,
            @NotNull Frequency frequency,
            @NotNull @Min(1) @Max(1000) Integer tenureCount,
            @NotNull LocalDate startDate) {
    }

    /** Loan row for lists and the client detail; client / area names let the Loans tab show who it is. */
    public record LoanSummary(Long id, String loanNo, BigDecimal principal, Frequency frequency,
                              BigDecimal installmentAmount, BigDecimal outstanding, BigDecimal overdueAmount,
                              Loan.Status status, Long clientId, String clientName, String areaName,
                              BigDecimal totalPayable, LocalDate endDate) {
    }

    public record LoanResponse(
            Long id, String loanNo, BigDecimal principal, Frequency frequency, BigDecimal installmentAmount,
            BigDecimal outstanding, BigDecimal overdueAmount, Loan.Status status,
            Long clientId, String clientName, BigDecimal interestRate, InterestMethod interestMethod,
            InterestCollection interestCollection, BigDecimal interestAmount, BigDecimal disbursedAmount,
            BigDecimal totalPayable, BigDecimal totalPaid, BigDecimal pendingPenalty, int tenureCount,
            LocalDate startDate, LocalDate originalEndDate, LocalDate currentEndDate, String legalNote,
            LocalDateTime closedAt) {
    }

    public record InstallmentResponse(Long id, int seqNo, LocalDate dueDate, BigDecimal dueAmount,
                                      BigDecimal principalComponent, BigDecimal interestComponent,
                                      BigDecimal principalBalanceAfter, BigDecimal paidAmount,
                                      BigDecimal penaltyAmount, BigDecimal penaltyPaid,
                                      InstallmentStatus status, boolean isExtension) {
        public static InstallmentResponse of(Installment i) {
            return new InstallmentResponse(i.getId(), i.getSeqNo(), i.getDueDate(), i.getDueAmount(),
                    i.getPrincipalComponent(), i.getInterestComponent(), i.getPrincipalBalanceAfter(),
                    i.getPaidAmount(), i.getPenaltyAmount(), i.getPenaltyPaid(), i.getStatus(), i.isExtension());
        }

        public static InstallmentResponse preview(LoanSchedule.Installment i) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            return new InstallmentResponse(null, i.seqNo(), i.dueDate(), i.dueAmount(), i.principalComponent(),
                    i.interestComponent(), i.principalBalanceAfter(), zero, zero, zero,
                    InstallmentStatus.PENDING, false);
        }
    }

    public record LoanPreview(BigDecimal interestAmount, BigDecimal disbursedAmount, BigDecimal totalPayable,
                              BigDecimal installmentAmount, LocalDate endDate,
                              List<InstallmentResponse> installments) {
    }

    public record DefaultRequest(@NotBlank @Size(max = 1000) String legalNote) {
    }
}
